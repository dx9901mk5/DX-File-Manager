# DX檔案管理器 (DX File Manager) — 本機開發與發布紀錄

- **最後更新時間**：2026-11-17
- **本機專案路徑**：`D:\Google雲端硬碟\Antigravity\DX檔案管理器`
- **GitHub 儲存庫**：https://github.com/dx9901mk5/DX_fileManager
- **Application ID**：`com.dx9901mk5.filemanager`
- **APK 產出名稱**：`DX_fileManager-v<versionName>-release.apk`（自下一版起自動帶入版本號）

---

## 1. 固定簽名資訊（覆蓋更新用）
- **本機金鑰檔案**：`app/release.keystore`（已列入 `.gitignore`，不推上公開儲存庫）
- **Key Alias**：`dx_key`
- **SHA-1 指紋**：`11:7A:E5:A9:C7:6D:61:F3:7D:BD:9A:20:A2:39:02:06:E6:E2:0F:B1`
- **SHA-256 指紋**：`20:70:09:9D:5A:BD:A3:4C:B0:46:79:86:BA:A9:BE:A3:89:30:B3:06:E6:02:B0:A6:4F:43:97:D6:77:A3:C4:1F`
- **GitHub Actions Secret**：`SIGNING_KEY_BASE64`（需存放 `app/release.keystore` 之 3,616 字元 Base64 編碼）

---

## 1.5 Google Cloud OAuth 與 GitHub Pages 設定
- **Google Cloud 專案**：`DX File Manager`（Project ID：`dx-file-manager-514912`）
- **已啟用 API**：`Google Drive API`
- **OAuth 同意畫面（Google Auth Platform）**：
  - 使用者類型：外部（External），狀態：**實際運作中（In production）**
  - 測試使用者名單：`dx9901mk5@gmail.com`
  - 授權網域：`dx9901mk5.github.io`
  - 首頁／條款連結：`https://dx9901mk5.github.io/DX-File-Manager/`（對應儲存庫 `docs/index.html`）
  - 隱私權政策連結：`https://dx9901mk5.github.io/DX-File-Manager/privacy.html`（對應儲存庫 `docs/privacy.html`）
- **Android OAuth 2.0 用戶端 ID**：`368366302108-fa6qt4rgrombf5uimu4cq3kqamcu9iic.apps.googleusercontent.com`（綁定套件 `com.dx9901mk5.filemanager` 與上述 SHA-1）
- **Microsoft Azure / OneDrive Client ID**：`5bea5b7f-d2fa-44e1-889d-bad83d2a3e77`（多租用戶＋個人 Microsoft 帳戶，Redirect URI：`dxfilemanager://oauth2redirect`）

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
- **v1.0.5** (`versionCode = 6`)：
  - 完成雲端掛載修復、無邊界半圓展開鈕、手機直向第三分區、左上角反白工具列、根目錄固定比例、Shizuku 授權監聽與 2秒/3秒分段手勢。
- **v1.0.14** (`versionCode = 15`)：
  - **金黃半透明玻璃風格 App 圖示 + 閃電標誌**：更新 `ic_launcher_background.xml` / `ic_launcher_foreground.xml` 為溫暖金黃光暈與半透明磨砂黃色玻璃資料夾，搭配 `DX⚡` 閃電徽章。
  - **1.5 秒長壓震動後下滑連選**：在 `TwoFingerStackGesture.kt` 與 `FileExplorerPane.kt` 新增 `onSweepSelectItem`，長壓 1.5 秒震動進入多選後，手指直接下滑經過的檔案依序震動圈選並飛入疊圖；於同一資料夾放開即保留多選與底部選單，不誤跳複製/移動視窗。
  - **延長雙指疊合動畫（約 1 秒）與放開確認後依序丟入目標資料夾動畫（約 2 秒）**：更新 `CrossPaneDragController.kt` 與 `MainActivity.kt`，多選飛入疊圖延長至約 1 秒；放開確認複製或移動後，疊圖檔案依序縮小飛入目標資料夾（約 2 秒過渡特效）。
- **v1.0.15** (`versionCode = 16`)：
  - **手機版第二、三分區頂部摺疊按鈕防擠出修復**：`FileExplorerPane.kt` 頂部圓角膠囊列中段圖示改為可水平捲動區域，並將右上角摺疊按鈕（`ChevronLeft` / `ChevronRight`）固定釘選在最右側，窄螢幕手機不再被擠出畫面、隨時可點擊摺疊與展開。
  - **新增「黑至灰黑玻璃漸層」深色主題**：於 `AppPreferences.kt`、`Theme.kt`、`DxI18n.kt`、`AboutAndUpdateDialog.kt` 與 `MainActivity.kt` 新增 `BLACK_TO_GRAY_GLASS` 選項（`#000000` → `#14161A` → `#282B32` 垂直漸層搭配半透明磨砂玻璃面板）。
  - Release 頁面：https://github.com/dx9901mk5/DX-File-Manager/releases/tag/v1.0.15
- **v1.0.16** (`versionCode = 17`)：
  - 長壓進入多選時間減半為 `750ms` (0.75 秒)。
  - Release 頁面：https://github.com/dx9901mk5/DX-File-Manager/releases/tag/v1.0.16
- **v1.0.21** (`versionCode = 22`，本機測試版)：
  - **系統資料夾與 `Android/data`、`Android/obb` 檢視**：`NavigationPane.kt` 新增「系統資料夾」快速入口（`Android/data`、`Android/obb`、`系統根目錄 (/)`）；`ShizukuApkInstaller.kt` 與 `LocalVfsProvider.kt` 整合 Shizuku Shell 權限讀取受保護目錄，未授權 Shizuku 時給予明確引導提示。
  - **AI 助理新增 DeepSeek、模型選擇與深度思考開關**：`AppPreferences.kt`、`AiAssistantClient.kt`、`AiFileAssistantDialog.kt` 與 `AboutAndUpdateDialog.kt` 新增 `DeepSeek`（`deepseek-chat` / `deepseek-reasoner`）、各家預設模型切換，以及「深度思考模式 (Thinking)」開關（支援 Gemini 2.5 `thinkingBudget`、DeepSeek Reasoner、Claude `thinking`、OpenAI/Grok `reasoning_effort`）。
  - **更新檢查新增雲端硬碟雙通道連結**：於「設定與關於我」更新區塊新增「檢查測試版雲端」（`https://drive.google.com/drive/folders/1h_I2rDnRJvTYe2V0_04AM9hAnk17wO5x`）與「檢查正式版雲端」（`https://drive.google.com/drive/folders/1JrrkNtKZZIGEvpLaHuXRxGiCRJPQO6Vu`）按鈕。
- **v1.0.20** (`versionCode = 21`，本機測試版)：
  - **OneDrive 一鍵瀏覽器登入（PKCE 授權碼流程）**：於 `OAuth2AuthHelper.kt` 內建 Azure 應用程式 Client ID（`5bea5b7f-d2fa-44e1-889d-bad83d2a3e77`）並改用標準 OAuth2 Authorization Code + PKCE（`response_type=code` 自動換取 `access_token`），用戶在雲端掛載精靈選擇 OneDrive 後，直接點擊「登入到 OneDrive」即可跳轉瀏覽器完成登入授權並自動導回 APP 掛載。
- **v1.0.19** (`versionCode = 20`，本機測試版)：
  - **檔案照片／影片／APK 實景縮圖預覽**：新增 `FileThumbnailIcon.kt`，於清單與圖示檢視即時載入相片縮圖（支援 EXIF 旋轉校正與 `LruCache` 快取）、影片首幀縮圖與 APK 圖示，其餘文件／試算表／投影片／PDF／壓縮檔／音訊皆依類型彩色區分。
  - **雙 App 並排跨 App 系統級拖曳分享**：新增 `SystemCrossAppDragHelper.kt`（`FileProvider` + `View.startDragAndDrop` + `DRAG_FLAG_GLOBAL | DRAG_FLAG_GLOBAL_URI_READ`），多視窗／並排模式下將檔案拖曳跨越視窗邊界時自動交棒給 Android 系統拖曳，可直接拖入通訊軟體、Word、試算表、投影片、社群 App 上傳。
- **v1.0.18** (`versionCode = 19`)：
  - **清空垃圾桶前二次確認 ＋ 倒垃圾音效與震動**：點擊垃圾桶「清空」按鈕先跳出確認對話框；確認清空時觸發 `DxHaptics.trashEmptyVibrate` 兩段式震動與 `DxSoundEffects.playTrashEmptySound` 合成倒垃圾音效（約 360ms 傾倒沙沙聲 + 收尾音）。
  - Release 頁面：https://github.com/dx9901mk5/DX_fileManager/releases/tag/v1.0.18
- **v1.0.17** (`versionCode = 18`)：
  - **底部選擇自動預留留白與捲動閃避**：`FileExplorerPane.kt` 偵測底部操作選單顯示或最後 3 個項目被選取時，自動平滑拉伸底部 `contentPadding` 至 `112.dp` 並自動捲動至最後選取項目，避免被底部功能列遮住重疊。
  - **AI 錯誤訊息友善化 ＋ 本機智慧整理備援**：針對 Gemini API `402`（預付額度用盡）等錯誤轉譯為清楚中文提示，並於 `AiAssistantClient.kt` / `AiFileAssistantDialog.kt` 新增「本機智慧整理（免金鑰 / 免額度）」離線分類功能。
  - **恢復 Dropbox / pCloud / Yandex.Disk 掛載選項**：於 `CloudMountWizardSheet.kt` 重新加入 `DROPBOX`、`PCLOUD`、`YANDEX`（連同既有的 `GOOGLE_DRIVE`、`ONEDRIVE`、`LAN_SMB`、`FTP`、`SFTP`、`WEBDAV`、`OWNCLOUD_NEXTCLOUD`、`AMAZON_S3` 共 11 種）。
  - **專案與 App 更名為 `DX_fileManager`**：更新 `strings.xml`（四語系）、`DxI18n.kt`、`settings.gradle.kts`、`app/build.gradle.kts`（產出 APK 名稱與 `GITHUB_REPO_URL` / `GITHUB_API_LATEST`）。
  - **雲端掛載精靈改為自動連線回報**：`CloudMountWizardSheet.kt` 於選擇帳號按下「下一步」後即自動連線驗證並顯示結果與自動掛載，移除手動「連線」按鈕步驟。
  - Release 頁面：https://github.com/dx9901mk5/DX_fileManager/releases/tag/v1.0.17
- **v1.0.13** (`versionCode = 14`)：
  - **第一區書籤與內部儲存空間樹狀展開**：書籤新增長壓確認移除；「內部儲存空間」新增展開/收合箭頭，可直接展開並點擊進入根目錄資料夾。
  - **拖曳至第一區根目錄即時放大鏡提示**：拖曳檔案至第一區的內部儲存空間、展開資料夾或書籤時即時顯示放大鏡與邊框提示，放開即詢問複製或移動。
  - **左上角鎖頭改為 AI 檔案分析與整理按鈕**：新增 `AiAssistantClient.kt` 與 `AiFileAssistantDialog.kt`，支援預設 **Gemini** 與 **GPT / Claude / Grok** 切換、首次引導選擇 AI 與填入 API 金鑰（設定頁同步新增 AI 切換與金鑰管理），並支援「一鍵套用整理計畫」自動建立資料夾並分類移動檔案。
  - **玻璃透明風格桌面圖示 & 左側分類圖示改色**：更新 `ic_launcher_background.xml` / `ic_launcher_foreground.xml` 為半透明玻璃質感；左側相片改為 Google 相簿四色風車、音訊紅色、文件藍色、APK 綠色、壓縮檔經典 ZIP 金黃拉鍊風格、應用程式藍色、垃圾桶白綠相間 Windows 資源回收桶風格。
  - **精簡雲端掛載清單**：`CloudMountWizardSheet.kt` 僅保留 Google 雲端硬碟與 OneDrive（及 6 種區網／自建協定）。
- **v1.0.12** (`versionCode = 13`)：
  - 調整返回鍵邏輯：多選模式下按下系統返回鍵優先取消多選並關閉底部選單，而非直接返回上一層目錄。
  - 強化拖曳懸停資料夾特效與跨分區提示：不論在來源分區或隔壁分區，拖曳滑過任一資料夾皆即時顯示放大鏡動畫提示；若於資料夾上持續停留滿 2 秒，自動進入該資料夾，直到手指放開才跳出「複製 / 移動 / 取消」確認對話框。
- **v1.0.11** (`versionCode = 12`)：
  - 修復跨區拖曳切換分區時，來源分區因摺疊被移出組合樹而中斷 `pointerInput`、導致多選疊合動畫卡在角落且無法操作之問題（`ThreePaneScaffold.kt` 新增 `keepAliveSourcePane` 保留手勢存活、`TwoFingerStackGesture.kt` 加入 `finally` 安全重置、`CrossPaneDragController.kt` 防呆 `Offset.Zero` 並保證動畫卡片清理）。
  - 放寬長壓 1.5 秒防抖容差（`1.8x touchSlop`）並於長壓/拖曳期間鎖定檔案點擊，確保滿 1.5 秒立即圈選第一個檔案並顯示底部操作選單排，不再誤觸開啟檔案。
  - 有複製/剪下檔案時，於第二分區與第三分區頂部工具列同步顯示「貼上」膠囊按鈕。
  - 修正 Google 雲端硬碟掛載精靈在系統 `GoogleAuthUtil` 未取得 Token 時誤判已登入之問題，於 Step 3 驗證 Token 有效性並於進階設定補齊 Google Drive 瀏覽器 OAuth Client ID 備援登入。
- **v1.0.10** (`versionCode = 11`)：
  - 全面改版「新增雲端 / 網路掛載」精靈 (`CloudMountWizardSheet.kt`) 為 5 步驟網路連線指引（連線類型 → 顯示名稱 → 登入 → 總覽 → 連線），支援同平台掛載多重帳號。
  - 整合 `play-services-auth` (`GoogleAuthUtil` + `AccountManager.newChooseAccountIntent`)，Google 雲端硬碟可直接呼叫系統 Google 帳戶選擇器登入授權，無需在主畫面手動輸入 Client ID；其餘雲端平台同步改為簡潔一鍵登入流程（進階 Token / Client ID 收納於折疊選單）。
- **v1.0.9** (`versionCode = 10`)：
  - 修復 `TwoFingerStackGesture.kt` 因 `pointerInput(Unit)` 捕獲舊狀態閉包導致長壓 1.5 秒無法進入多選與拖曳狀態的問題，改用 `composed` + `rememberUpdatedState` 即時同步最新檔案列表與座標狀態。
- **v1.0.8** (`versionCode = 9`)：
  - 新增檔案傳輸（複製 / 移動 / 上傳 / 下載）通知欄即時進度條 (`TransferNotificationHelper.kt`)，支援 Android 13+ 通知權限請求與位元組串流進度回報。
  - 移除左上角「根目錄」標題列的重複齒輪設定按鈕，保留下方導覽工具列的設定齒輪圖示。
  - 檔案與資料夾清單改為純依「資料夾優先 + 檔名」排序，移除原本依月份分組的標題列。
  - 修復長壓 1.5 秒進入多選與震動失效問題：改用 `PointerEventPass.Initial` 攔截與 `boundsInRoot()` 精準座標命中，並於 `DxHaptics` 補齊 `View.performHapticFeedback` 雙重震動支援。
  - 新增系統返回手勢處理 (`BackHandler`)：依目前點擊聚焦的分區退回上層目錄，若已達根目錄退無可退再按返回則詢問是否退出 App。
- **v1.0.7** (`versionCode = 8`)：
  - 資料夾圖示全面改版為 iOS 27 液態玻璃風格 (`IosGlassFolderIcon.kt`)，並透過 `VfsFile.isEmpty` 區分「空資料夾」（通透中空玻璃）與「有內容物資料夾」（內含層疊文件卡片與暖金玻璃折射光澤）。
  - 三大分區 (`ThreePaneScaffold.kt`) 摺疊／展開全面加入彈簧回彈 (`spring`) 動畫，半圓展開按鈕同步支援彈簧縮放進出場。
  - 清單與網格檢視 (`DxOverscrollBounce.kt`) 新增 iOS 風格垂直滑動至頂部／底部的阻尼拉伸與彈簧回彈效果。
  - 暗色主題 (`Theme.kt` / `AppPreferences.kt` / 設定頁) 新增兩種底色切換：**全黑 (OLED 螢幕適用)** (`#000000`) 與 **灰黑底** (`#18191C` / `#202226`)。
  - 多選與觸控手勢重構 (`TwoFingerStackGesture.kt` / `CrossPaneDragController.kt` / `MainActivity.kt`)：長壓 1.5 秒大震動進入多選並顯示底部操作選單；手指未離開時第二指點擊連續多選並播放檔案聚合飛入動畫，底部選單暫時隱藏；第一指挪動即進入多檔案拖曳（資料夾顯示短暫放大鏡特效、移至分區展開鈕自動展開目標分區並摺疊原分區），放開詢問複製或移動；未拖曳原地放開則顯示優先含「新增資料夾 / 壓縮」的底部選單。
  - 第二、三分區頂部工具列新增「新增空白資料夾」圖示按鈕。
- **v1.0.6** (`versionCode = 7`)：
  - 修復 `TwoFingerStackGesture.kt` 誤用 `kotlinx.coroutines.withTimeoutOrNull` 導致進入多選時當機的問題，改用 `AwaitPointerEventScope` 內建 `withTimeoutOrNull` 並於 `PointerEventPass.Initial` 攔截第二指點擊。
  - 修復 `CrossPaneDragController.kt` 單一 `flyingCard` 覆蓋問題，改為 `mutableStateListOf<FlyingStackCard>` 支援連續多指點擊的卡片飛入疊合動畫。
  - 產出 APK 檔名正式帶入版本號：`DX-File-Manager-v1.0.6-release.apk`（53,652,909 Bytes，固定簽名）。
  - GitHub Actions Run：`#43837808033` (`success`)
  - Release 頁面：https://github.com/dx9901mk5/DX-File-Manager/releases/tag/v1.0.6
  - 全面檢查並修復 14 種雲端/網路掛載：移除「下載更多 / 外掛擴充」按鈕；新增 `OAuth2AuthHelper` 瀏覽器登入授權回調 (`dxfilemanager://oauth2redirect`) 與「測試連線」驗證；補齊 `FTPClient` (commons-net)、Dropbox、OneDrive、Box、pCloud、Yandex、WebDAV/OwnCloud/Nextcloud、Amazon S3 真實 API 串接，並於第一區已掛載雲端旁加上移除按鈕 (`✕`)。
  - 移除左下角「工具箱」區塊，將「DX 儲存空間分析器」與「DX 區域網路網頁分享」整合至設定頁面內。
  - 摺疊後完全消除垂直邊界與側條，改為在左緣／右緣／頂部浮動顯示一個小半圓形金黃色展開按鈕；手機直向同步開放第三分區，預設為摺疊狀態並於右緣顯示半圓展開鈕。
  - 左上角導覽工具列修正暗主題圖示反白顯示，並簡化為最左邊「齒輪設定」接著「房子首頁」與「雙欄切換」；「主題切換」與「鍵盤快捷鍵說明」皆移入設定頁面內。
  - 根目錄（第一區）預設寬度固定為畫面比例約 `24%`（`180dp ~ 240dp`）且永久鎖定不予拖曳變更。
  - 設定內「使用 Shizuku 靜默安裝 APK」開關加入 `OnRequestPermissionResultListener` 授權監聽：需先偵測並獲得 Shizuku 授權後才啟用開關。
  - 手勢與跨區拖曳重構：滑動過程絕不誤觸選擇；必須原地停住滿 2 秒給予「大振動」後才進入選擇拖曳狀態，若繼續完全不動達 3 秒才彈出完整功能選單（複製、開啟方式等）；一旦開始拖動過程絕不觸發選單，第二指多選疊加給予「小震動」；拖曳至各分區半圓展開按鈕時按鈕放大 1.35x 並自動展開目標分區、摺疊先前分區，直到手指確實放開才跳出「複製 / 移動 / 取消」。

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

---

## 4. 待辦與進行中紀錄（接續對話用）
- **最新已發布版本**：`v1.0.12` (`versionCode = 13`)，Release 頁面：https://github.com/dx9901mk5/DX-File-Manager/releases/tag/v1.0.12
- **本機已修改（尚未打包發布為 `v1.0.13`）**：
  - `CloudMountWizardSheet.kt`：雲端硬碟精簡為僅保留 **Google 雲端硬碟** 與 **OneDrive**（以及 6 種區網／自建協定：SMB、FTP、SFTP、WebDAV、OwnCloud/Nextcloud、Amazon S3），移除 Dropbox、Yandex、Box、pCloud、SugarSync、MediaFire。
- **Google 雲端硬碟 OAuth 狀態**：
  - 已完成 Google Cloud Console（`dx-file-manager-514912`）Android OAuth 用戶端建立與 GitHub Pages 品牌宣傳設定，目前為「實際運作中（In production）」（未經 Google CASA 驗證前上限 100 位使用者；解除上限需至驗證中心送審 + YouTube 示範影片 + CASA 評估）。
- **OneDrive (Azure) 註冊待辦**：
  - 帳號 `dx9901mk5@gmail.com` 因尚未建立 Microsoft Entra / Azure 租用戶，開啟 `entra.microsoft.com` 時出現租用戶錯誤；需改從 `https://portal.azure.com/` 登入建立免費租用戶後，建立「多租戶 + 個人 Microsoft 帳號」之應用程式註冊（Redirect URI：`dxfilemanager://oauth2redirect`，勾選 Access tokens 與 `Files.ReadWrite.All`），再將取得的 Client ID 寫入 `OAuth2AuthHelper.kt` 並發布 `v1.0.13`。
