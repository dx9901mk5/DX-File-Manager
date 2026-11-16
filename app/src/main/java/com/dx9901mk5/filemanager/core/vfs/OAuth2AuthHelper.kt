package com.dx9901mk5.filemanager.core.vfs

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.URLEncoder

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
    private const val GOOGLE_DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive"

    private val _receivedToken = MutableStateFlow<String?>(null)
    val receivedToken = _receivedToken.asStateFlow()

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
                    "系統 Google 授權失敗 ($detail)：本機開發簽名尚未於 Google Cloud Console 註冊 Android OAuth 用戶端，請於下方進階設定填入 OAuth Client ID 改用瀏覽器登入，或貼上 Access Token。"
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
                ?: data.getQueryParameter("code")

            val token = fragmentToken ?: queryToken
            if (!token.isNullOrBlank()) {
                _receivedToken.value = token
            }
        }
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
                val scope = URLEncoder.encode("Files.ReadWrite.All offline_access", "UTF-8")
                "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=$cid&redirect_uri=$redirect&response_type=token&scope=$scope"
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
        require(clientId.isNotBlank()) { "請展開進階設定填入 OAuth Client ID，或直接貼上 Access Token" }
        val url = buildAuthorizeUrl(protocol, clientId)
            ?: throw UnsupportedOperationException("${protocol.displayName} 不使用 OAuth2 瀏覽器授權")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
