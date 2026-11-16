# DX檔案管理器 (DX File Manager) — 本機開發與發布紀錄

- **最後更新時間**：2026-11-16
- **本機專案路徑**：`D:\Google雲端硬碟\Antigravity\DX檔案管理器`
- **GitHub 儲存庫**：https://github.com/dx9901mk5/DX-File-Manager
- **Application ID**：`com.dx9901mk5.filemanager`
- **APK 產出名稱**：`DX-File-Manager-release.apk`

---

## 1. 固定簽名資訊（覆蓋更新用）
- **本機金鑰檔案**：`app/release.keystore`（已列入 `.gitignore`，不推上公開儲存庫）
- **Key Alias**：`dx_key`
- **SHA-256 指紋**：`20:70:09:9D:5A:BD:A3:4C:B0:46:79:86:BA:A9:BE:A3:89:30:B3:06:E6:02:B0:A6:4F:43:97:D6:77:A3:C4:1F`
- **GitHub Actions Secret**：`SIGNING_KEY_BASE64`（需存放 `app/release.keystore` 之 3,616 字元 Base64 編碼）

---

## 2. 版本發布紀錄
- **v1.0.0** (`versionCode = 1`)：
  - 完成 Step 1～Step 4 全架構實作：三欄自適應佈局、雙指堆疊拖曳、滑鼠拉框與無衝突快捷鍵、VFS 14 種雲端/區網掛載、ZIP/7Z/TAR 壓縮與 AES-256 `.dxe` 加解密、垃圾桶 30 天自動清理、App APK/Split APKs 備份、Shizuku 靜默安裝、GitHub Releases 更新檢查、四國語言（繁體中文、簡體中文、英文、日文）與 DX 區網網頁分享／儲存空間分析器。
- **v1.0.1** (`versionCode = 2`)：
  - APK 改名為 `DX-File-Manager-release.apk`。
  - 新增黑底金黃「`DX` 資料夾」自適應桌面圖示。
  - 暗色主題文字調整為純白高對比（`#FFFFFF`）。
  - 支援手機／摺疊／平板 DPI 比例與分區展開狀態記憶（手機預設僅展開第 2 區，平板／展開摺疊預設三區全開）。
  - 三區上方皆具備摺疊/展開按鈕、分隔線拖曳把手加寬至 `18.dp`，預留狀態列與 Dock 安全內縮空間，並於面板上方加入「清單 / 小圖示 / 大圖示」切換鈕。
- **v1.0.2** (`versionCode = 3`)：
  - 第一分區預設寬度縮小為第二、三分區的 `1/3`（約 `14.3%`）。
  - 第二、三分區改版為圓角膠囊頂部列與雙行檔案列（左側圓角方塊圖示底框、右側大小與 `yyyy年M月d日 a h:mm:ss` 時間），右下角金黃色圓形 `+` 按鈕。
- **v1.0.3** (`versionCode = 4`)：
  - 已使用 `SIGNING_KEY_BASE64` 固定簽名金鑰（SHA-256：`20:70:09:9D:5A:BD:A3:4C:B0:46:79:86:BA:A9:BE:A3:89:30:B3:06:E6:02:B0:A6:4F:43:97:D6:77:A3:C4:1F`）成功編譯並發布 Release（Run `#43822376934`）。
  - Release 頁面：https://github.com/dx9901mk5/DX-File-Manager/releases/tag/v1.0.3
  - 後續所有版本皆可直接覆蓋安裝更新。
- **v1.0.4** (`versionCode = 5`)：
  - 三區右上角補上專屬摺疊鈕，摺疊後頂部顯示顯眼金黃色圓形展開按鈕。
  - 手機直向預設僅顯示第 1、2 區（無第 3 區），打橫時自動顯示第 3 區。
  - 平板／摺疊預設三區全開，中間分隔線預設鎖定固定，需點擊左上角鎖頭按鈕才進入拖曳比例編輯模式。
  - 左上角配置 App 設定按鈕（內含「關於我」與「檢查更新」）。
  - 長壓進入選擇／選單改為 2 秒（`2000ms`）並加入震動提示，選單每個項目皆附帶對應圖示。
  - 第一區「圖片 / 影片 / 相片」整合為單一「相片與影片」入口，進入後上方提供「全部 / 照片 / 影片」子分類篩選。
- **待確認變更（尚未發布 APK）**：
  - 全面檢查並修復 14 種雲端/網路掛載：移除「下載更多 / 外掛擴充」按鈕；新增 `OAuth2AuthHelper` 瀏覽器登入授權回調 (`dxfilemanager://oauth2redirect`) 與「測試連線」驗證；補齊 `FTPClient` (commons-net)、Dropbox、OneDrive、Box、pCloud、Yandex、WebDAV/OwnCloud/Nextcloud、Amazon S3 真實 API 串接，並於第一區已掛載雲端旁加上移除按鈕 (`✕`)。
  - 移除左下角「工具箱」區塊，將「DX 儲存空間分析器」與「DX 區域網路網頁分享」整合至設定頁面內。
  - 摺疊後完全消除垂直邊界與側條，改為在左緣／右緣／頂部浮動顯示一個小半圓形金黃色展開按鈕；手機直向同步開放第三分區，預設為摺疊狀態並於右緣顯示半圓展開鈕。
  - 左上角導覽工具列修正暗主題圖示反白顯示，並簡化為最左邊「齒輪設定」接著「房子首頁」與「雙欄切換」；「主題切換」與「鍵盤快捷鍵說明」皆移入設定頁面內。
  - 根目錄（第一區）預設寬度固定為畫面比例約 `24%`（`180dp ~ 240dp`）且永久鎖定不予拖曳變更。
  - 設定內「使用 Shizuku 靜默安裝 APK」開關加入 `OnRequestPermissionResultListener` 授權監聽：需先偵測並獲得 Shizuku 授權後才啟用開關。

---

## 3. 核心檔案索引
- 應用程式進入點：`app/src/main/java/com/dx9901mk5/filemanager/MainActivity.kt`
- 狀態管理：`app/src/main/java/com/dx9901mk5/filemanager/ui/FileManagerViewModel.kt`
- 三欄佈局與分隔線：`app/src/main/java/com/dx9901mk5/filemanager/ui/layout/ThreePaneScaffold.kt`
- 導覽與檔案面板：`NavigationPane.kt`、`FileExplorerPane.kt`
- 觸控與鍵鼠手勢：`TwoFingerStackGesture.kt`、`CrossPaneDragController.kt`、`MouseKeyboardHandler.kt`
- 偏好設定與多語系：`AppPreferences.kt`、`DxI18n.kt`
- 雲端與區網 VFS：`VfsModels.kt`、`LocalVfsProvider.kt`、`NetworkVfsProviders.kt`、`VfsRegistry.kt`
- 區網網頁分享伺服器：`DxWebShareServer.kt`
- 壓縮加密與垃圾桶：`ArchiveAndCryptoManager.kt`、`TrashDatabase.kt`、`TrashManager.kt`、`TrashCleanupWorker.kt`
- Shizuku 與更新檢查：`ShizukuApkInstaller.kt`、`UpdateChecker.kt`
- CI/CD 自動編譯發布：`.github/workflows/build.yml`
