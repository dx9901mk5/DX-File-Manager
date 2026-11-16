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
  - 修正 `.github/workflows/build.yml` Base64 解碼過濾空白字元，準備以固定簽名金鑰重新發布（待確認 `SIGNING_KEY_BASE64` 更新後重推）。

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
