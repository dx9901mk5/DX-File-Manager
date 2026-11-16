package com.dx9901mk5.filemanager.core.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class FileHashes(
    val md5: String,
    val sha1: String,
    val sha256: String
)

object ArchiveAndCryptoManager {

    private const val DXE_MAGIC = "DXE1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    /**
     * 壓縮為 ZIP（可選 AES-256 密碼保護）
     */
    suspend fun compressToZip(
        sources: List<File>,
        outputZip: File,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = if (!password.isNullOrEmpty()) {
                ZipFile(outputZip, password.toCharArray())
            } else {
                ZipFile(outputZip)
            }
            val params = ZipParameters().apply {
                if (!password.isNullOrEmpty()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }
            for (src in sources) {
                if (src.isDirectory) {
                    zip.addFolder(src, params)
                } else {
                    zip.addFile(src, params)
                }
            }
            outputZip
        }
    }

    /**
     * 解壓縮 (.zip / .7z / .tar / .tar.gz / .tgz)
     */
    suspend fun extractArchive(
        archiveFile: File,
        destDir: File,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!destDir.exists()) destDir.mkdirs()
            val lower = archiveFile.name.lowercase()
            when {
                lower.endsWith(".zip") -> {
                    val zip = if (!password.isNullOrEmpty()) {
                        ZipFile(archiveFile, password.toCharArray())
                    } else {
                        ZipFile(archiveFile)
                    }
                    zip.extractAll(destDir.absolutePath)
                }
                lower.endsWith(".7z") -> {
                    SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
                        var entry = sevenZ.nextEntry
                        while (entry != null) {
                            val outFile = File(destDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { out ->
                                    val buffer = ByteArray(8192)
                                    var read: Int
                                    while (sevenZ.read(buffer).also { read = it } > 0) {
                                        out.write(buffer, 0, read)
                                    }
                                }
                            }
                            entry = sevenZ.nextEntry
                        }
                    }
                }
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> {
                    GzipCompressorInputStream(FileInputStream(archiveFile)).use { gzIn ->
                        extractTarStream(TarArchiveInputStream(gzIn), destDir)
                    }
                }
                lower.endsWith(".tar") -> {
                    TarArchiveInputStream(FileInputStream(archiveFile)).use { tarIn ->
                        extractTarStream(tarIn, destDir)
                    }
                }
                else -> throw UnsupportedOperationException("不支援的壓縮格式: ${archiveFile.name}")
            }
            destDir
        }
    }

    private fun extractTarStream(tarIn: TarArchiveInputStream, destDir: File) {
        var entry: TarArchiveEntry? = tarIn.nextEntry
        while (entry != null) {
            val outFile = File(destDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { out ->
                    tarIn.copyTo(out)
                }
            }
            entry = tarIn.nextEntry
        }
    }

    /**
     * 獨立 .dxe (AES-GCM 256-bit) 檔案加密
     */
    suspend fun encryptFileDxe(sourceFile: File, password: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val salt = ByteArray(SALT_LEN).apply { SecureRandom().nextBytes(this) }
                val iv = ByteArray(IV_LEN).apply { SecureRandom().nextBytes(this) }
                val key = deriveAes256Key(password, salt)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

                val plainBytes = sourceFile.readBytes()
                val cipherBytes = cipher.doFinal(plainBytes)

                val outFile = File(sourceFile.parentFile, "${sourceFile.name}.dxe")
                FileOutputStream(outFile).use { out ->
                    out.write(DXE_MAGIC.toByteArray(Charsets.US_ASCII))
                    out.write(salt)
                    out.write(iv)
                    out.write(cipherBytes)
                }
                outFile
            }
        }

    /**
     * 獨立 .dxe (AES-GCM 256-bit) 檔案解密
     */
    suspend fun decryptFileDxe(encryptedFile: File, password: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val allBytes = encryptedFile.readBytes()
                val magicLen = DXE_MAGIC.length
                require(allBytes.size > magicLen + SALT_LEN + IV_LEN) { "無效的 .dxe 加密檔案" }
                val magic = String(allBytes, 0, magicLen, Charsets.US_ASCII)
                require(magic == DXE_MAGIC) { "檔案格式不符 ($magic)" }

                val salt = allBytes.copyOfRange(magicLen, magicLen + SALT_LEN)
                val iv = allBytes.copyOfRange(magicLen + SALT_LEN, magicLen + SALT_LEN + IV_LEN)
                val cipherBytes = allBytes.copyOfRange(magicLen + SALT_LEN + IV_LEN, allBytes.size)

                val key = deriveAes256Key(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                val plainBytes = cipher.doFinal(cipherBytes)

                val targetName = encryptedFile.name.removeSuffix(".dxe").ifBlank { "${encryptedFile.name}.dec" }
                val outFile = File(encryptedFile.parentFile, targetName)
                outFile.writeBytes(plainBytes)
                outFile
            }
        }

    private fun deriveAes256Key(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val secret = factory.generateSecret(spec).encoded
        return SecretKeySpec(secret, "AES")
    }

    /**
     * 計算檔案 MD5 / SHA-1 / SHA-256 雜湊值
     */
    suspend fun computeHashes(file: File): Result<FileHashes> = withContext(Dispatchers.IO) {
        runCatching {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16 * 1024)
            FileInputStream(file).use { input ->
                var read: Int
                while (input.read(buf).also { read = it } > 0) {
                    md5.update(buf, 0, read)
                    sha1.update(buf, 0, read)
                    sha256.update(buf, 0, read)
                }
            }
            FileHashes(
                md5 = md5.digest().toHex(),
                sha1 = sha1.digest().toHex(),
                sha256 = sha256.digest().toHex()
            )
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
