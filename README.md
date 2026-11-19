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

- 前往 [GitHub Releases](https://github.com/dx9901mk5/DX_fileManager/releases/latest) 下載最新簽名版 `DX_fileManager-v<version>-release.apk`（固定簽名，可直接覆蓋更新）。
- App 內「設定與關於我」亦內建自動更新檢查，可自動偵測 GitHub Releases 與雲端硬碟最新版本並顯示下載進度條。
- Download the latest signed APK from [GitHub Releases](https://github.com/dx9901mk5/DX_fileManager/releases/latest) (consistent signing key for direct in-place upgrades), or use the built-in updater in **Settings & About**.

---

## 權限說明 / Permissions

- `MANAGE_EXTERNAL_STORAGE`（所有檔案存取權限）：用於瀏覽、搬移、壓縮與管理裝置內部儲存空間檔案。
- `REQUEST_INSTALL_PACKAGES`（安裝未知應用）：用於直接安裝本機或下載的 APK 檔案（含自動更新）。
- `INSTALL_SHORTCUT`：用於將任意檔案／資料夾建立至桌面捷徑。
- 可於 App 內「設定與關於我 → 系統授權與權限連動」一鍵跳轉系統設定並自動定位至本 App。

---

## 開發者 / Developer

- **dx9901mk5** — [GitHub Repository](https://github.com/dx9901mk5/DX_fileManager)
