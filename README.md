# DX檔案管理器 (DX File Manager)

一個專為 Android 設計的高效率多視窗／多面板檔案管理器，結合本機檔案管理、雲端與網路掛載、自由小視窗拖曳桌面捷徑、Shizuku 靜默安裝與 AI 檔案整理助理，並支援繁體中文、简体中文、English、日本語四國語言即時切換。

A high-efficiency multi-pane & freeform-window file manager for Android featuring local storage management, cloud/network mounts, drag-to-desktop shortcuts, Shizuku silent APK installation, AI file organization assistant, and instant 4-language UI switching (Traditional Chinese, Simplified Chinese, English, Japanese).

---

## 主要功能 / Key Features

- **多面板與自由小視窗操作 / Multi-Pane & Freeform Window**
  - 支援雙／三面板瀏覽、側邊欄快速導覽與自訂比例；可開啟多個 `WINDOWING_MODE_FREEFORM` 自由小視窗進行跨視窗與跨 App 拖曳。
  - Dual/three-pane browsing with customizable ratios, plus multiple `WINDOWING_MODE_FREEFORM` small windows for cross-window and cross-app drag-and-drop.
- **雙指／滑過多選與拖曳手勢 / Multi-Select & Drag Gestures**
  - 長壓 `0.75s` 進入多選，支援第二指點擊與滑過多選；手指停頓 `350ms` 後再移動即鎖定為純拖曳模式，避免誤選其他檔案。
  - Long-press (`0.75s`) to enter multi-select with second-finger tap and sweep-select; pausing for `350ms` and moving locks into pure drag mode.
- **任意檔案／資料夾桌面捷徑 / Drag-to-Desktop Shortcuts**
  - 拖曳任何檔案或資料夾出小視窗至桌面（或從功能表／底部操作列點擊「在桌面建立捷徑」），支援圖片／影片縮圖、APK 圖示與各類檔案專屬圖示，多選複合類別時自動批次建立。
  - Drag any file or folder out to the launcher desktop (or use the context menu / bottom bar action) to pin shortcuts with real thumbnails, APK icons, or type badges; multi-select supports batch shortcut creation.
- **雲端與網路掛載 / Cloud & Network Mounts**
  - 支援 Google Drive（OAuth 2.0）、OneDrive（PKCE）、SMB、SFTP、FTP、WebDAV。
  - Built-in support for Google Drive (OAuth 2.0), OneDrive (PKCE), SMB, SFTP, FTP, and WebDAV.
- **AI 檔案整理助理 / AI File Assistant**
  - 支援 Gemini、DeepSeek、GPT (OpenAI)、Claude、Grok 與深度思考模式，協助分類、重命名與整理建議。
  - Integrates Gemini, DeepSeek, GPT (OpenAI), Claude, and Grok with optional thinking mode for smart organization.
- **Shizuku 靜默安裝與應用管理 / Shizuku Silent Install & App Manager**
  - 支援透過 Shizuku 靜默批次安裝 APK、已安裝應用檢視與備份匯出。
  - Batch silent APK installation via Shizuku, plus installed app inspection and APK backup export.
- **垃圾桶與加解密壓縮 / Recycle Bin, Crypto & Archives**
  - 內建 `.dx_trash` 30 天自動清理垃圾桶、ZIP/7Z/TAR 壓縮解壓縮與 `.dxe` AES-256 檔案加解密。
  - 30-day `.dx_trash` recycle bin, ZIP/7Z/TAR archive support, and `.dxe` AES-256 encryption/decryption.

---

## 下載與安裝 / Download & Install

前往 [GitHub Releases](https://github.com/dx9901mk5/DX_fileManager/releases/latest) 可下載以下兩個分離版本（皆為固定簽名，可直接覆蓋更新）：

1. **DX 檔案管理器 GitHub 獨立版（主程式）**：`DX_fileManager-v<version>-full-release.apk`
   - 分離完整版主程式，App 內「設定與關於我」內建自動檢查 GitHub Releases 更新並下載安裝。
2. **DX Shizuku 輔助安裝器（DX 專屬輔助用安裝器外掛）**：`DX_Shizuku_Helper-v<version>-release.apk`
   - 本 App 為 **DX檔案管理器 專屬的 Shizuku 輔助用安裝器**，**同時支援 GitHub 版與 Google Play 版主程式**。
   - **支援 Google Play 版觸發能力**：安裝此輔助安裝器並授權 Shizuku 後，Google Play 版 DX檔案管理器 會自動偵測此外掛；當您在 Play 版內長壓任何 `.apk` 檔案時，功能選單會自動出現 **「用 DX Shizuku 靜默安裝」** 選項供選擇安裝 APK。
   - **外掛自我更新**：DX Shizuku 安裝助手內建「檢查外掛更新 (GitHub)」按鈕，可自動檢查 GitHub 最新外掛版本並下載安裝。

- Download the signed APKs from [GitHub Releases](https://github.com/dx9901mk5/DX_fileManager/releases/latest):
  - **`DX_fileManager-v<version>-full-release.apk`**: Standalone GitHub full edition with built-in GitHub auto-updater.
  - **`DX_Shizuku_Helper-v<version>-release.apk`**: Dedicated Shizuku helper installer plugin for **DX File Manager** (works with both the **GitHub edition** and the **Google Play edition**—when installed, the Play edition automatically detects the plugin and reveals the Shizuku APK install option in the context menu; includes built-in GitHub self-update check & download/install).

---

## 權限說明 / Permissions

- `MANAGE_EXTERNAL_STORAGE`（所有檔案存取權限）：用於瀏覽、搬移、壓縮與管理裝置內部儲存空間檔案。
- `REQUEST_INSTALL_PACKAGES`（安裝未知應用）：用於直接安裝本機或下載的 APK 檔案（含自動更新）。
- `INSTALL_SHORTCUT`：用於將任意檔案／資料夾建立至桌面捷徑。
- 可於 App 內「設定與關於我 → 系統授權與權限連動」一鍵跳轉系統設定並自動定位至本 App。

---

## 開發者 / Developer

- **dx9901mk5** — [GitHub Repository](https://github.com/dx9901mk5/DX_fileManager)
