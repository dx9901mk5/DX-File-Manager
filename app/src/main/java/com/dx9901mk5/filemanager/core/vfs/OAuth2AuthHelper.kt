package com.dx9901mk5.filemanager.core.vfs

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

sealed class GoogleTokenResult {
    data class Success(val token: String, val accountName: String) : GoogleTokenResult()
    data class Recoverable(val intent: Intent, val accountName: String) : GoogleTokenResult()
    data class Error(val message: String) : GoogleTokenResult()
}

/**
 * 統一 OAuth2 登入與系統 Google 帳戶授權輔助器 (Redirect URI: dxfilemanager://oauth2redirect)
 */
object OAuth2AuthHelper {
    const val REDIRECT_URI = "dxfilemanager://oauth2redirect"
    const val ONEDRIVE_CLIENT_ID = "5bea5b7f-d2fa-44e1-889d-bad83d2a3e77"
    private const val GOOGLE_DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive"

    private val _receivedToken = MutableStateFlow<String?>(null)
    val receivedToken = _receivedToken.asStateFlow()

    @Volatile
    private var pendingOneDriveVerifier: String? = null

    @Volatile
    private var pendingOneDriveClientId: String? = null

    private val httpClient by lazy { OkHttpClient() }

    fun clearToken() {
        _receivedToken.value = null
    }

    fun createGoogleAccountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
    }

    fun refreshGoogleDriveTokenSync(
        context: Context,
        accountName: String,
        expiredToken: String?
    ): String? {
        if (accountName.isBlank()) return null
        return runCatching {
            if (!expiredToken.isNullOrBlank()) {
                runCatching { GoogleAuthUtil.clearToken(context, expiredToken) }
            }
            val account = Account(accountName, "com.google")
            GoogleAuthUtil.getToken(context, account, GOOGLE_DRIVE_SCOPE)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    suspend fun fetchGoogleDriveToken(context: Context, accountName: String): GoogleTokenResult =
        withContext(Dispatchers.IO) {
            try {
                val account = Account(accountName, "com.google")
                val token = GoogleAuthUtil.getToken(context, account, GOOGLE_DRIVE_SCOPE)
                GoogleTokenResult.Success(token = token, accountName = accountName)
            } catch (e: UserRecoverableAuthException) {
                val intent = e.intent
                if (intent != null) {
                    GoogleTokenResult.Recoverable(intent = intent, accountName = accountName)
                } else {
                    GoogleTokenResult.Error(e.message ?: "需要使用者授權")
                }
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                GoogleTokenResult.Error(
                    "Google 帳號授權失敗 ($detail)，請確認裝置已登入 Google 帳號並允許雲端硬碟存取權限。"
                )
            }
        }

    fun handleRedirectIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme == "dxfilemanager" && data.host == "oauth2redirect") {
            val fragment = data.fragment.orEmpty()
            val fragmentToken = fragment.split("&")
                .mapNotNull {
                    val kv = it.split("=", limit = 2)
                    if (kv.size == 2) kv[0] to Uri.decode(kv[1]) else null
                }
                .toMap()["access_token"]

            val queryToken = data.getQueryParameter("access_token")
            val authCode = data.getQueryParameter("code")
            val verifier = pendingOneDriveVerifier
            val clientId = pendingOneDriveClientId

            if (!queryToken.isNullOrBlank() || !fragmentToken.isNullOrBlank()) {
                _receivedToken.value = fragmentToken ?: queryToken
            } else if (!authCode.isNullOrBlank() && !verifier.isNullOrBlank() && !clientId.isNullOrBlank()) {
                pendingOneDriveVerifier = null
                pendingOneDriveClientId = null
                exchangeOneDriveCodeForToken(authCode, verifier, clientId)
            } else if (!authCode.isNullOrBlank()) {
                _receivedToken.value = authCode
            }
        }
    }

    private fun exchangeOneDriveCodeForToken(code: String, codeVerifier: String, clientId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val form = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", codeVerifier)
                    .add("scope", "Files.ReadWrite.All offline_access User.Read")
                    .build()
                val req = Request.Builder()
                    .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                    .post(form)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val token = JSONObject(bodyStr).optString("access_token")
                        if (token.isNotBlank()) {
                            _receivedToken.value = token
                        }
                    }
                }
            }
        }
    }

    private fun generatePkcePair(): Pair<String, String> {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return verifier to challenge
    }

    fun buildAuthorizeUrl(protocol: VfsProtocol, clientId: String): String? {
        val cid = URLEncoder.encode(clientId.trim(), "UTF-8")
        val redirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return when (protocol) {
            VfsProtocol.GOOGLE_DRIVE -> {
                val scope = URLEncoder.encode("https://www.googleapis.com/auth/drive", "UTF-8")
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=$cid&redirect_uri=$redirect&response_type=token&scope=$scope"
            }
            VfsProtocol.DROPBOX -> {
                "https://www.dropbox.com/oauth2/authorize?client_id=$cid&redirect_uri=$redirect&response_type=token"
            }
            VfsProtocol.ONEDRIVE -> {
                val (verifier, challenge) = generatePkcePair()
                pendingOneDriveVerifier = verifier
                pendingOneDriveClientId = clientId.trim()
                val scope = URLEncoder.encode("Files.ReadWrite.All offline_access User.Read", "UTF-8")
                "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=$cid&redirect_uri=$redirect&response_type=code&scope=$scope&code_challenge=$challenge&code_challenge_method=S256"
            }
            VfsProtocol.YANDEX -> {
                "https://oauth.yandex.com/authorize?response_type=token&client_id=$cid&redirect_uri=$redirect"
            }
            VfsProtocol.BOX -> {
                "https://account.box.com/api/oauth2/authorize?response_type=code&client_id=$cid&redirect_uri=$redirect"
            }
            VfsProtocol.PCLOUD -> {
                "https://my.pcloud.com/oauth2/authorize?client_id=$cid&response_type=token&redirect_uri=$redirect"
            }
            else -> null
        }
    }

    fun launchBrowserAuth(context: Context, protocol: VfsProtocol, clientId: String): Result<Unit> = runCatching {
        val effectiveClientId = clientId.trim().ifBlank {
            if (protocol == VfsProtocol.ONEDRIVE) ONEDRIVE_CLIENT_ID else ""
        }
        require(effectiveClientId.isNotBlank()) { "請展開進階設定填入 OAuth Client ID，或直接貼上 Access Token" }
        val url = buildAuthorizeUrl(protocol, effectiveClientId)
            ?: throw UnsupportedOperationException("${protocol.displayName} 不使用 OAuth2 瀏覽器授權")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
