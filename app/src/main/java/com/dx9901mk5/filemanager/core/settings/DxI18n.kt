package com.dx9901mk5.filemanager.core.settings

import java.util.Locale

enum class AppLanguage(val code: String, val label: String) {
    ZH_TW("zh-TW", "繁體中文"),
    ZH_CN("zh-CN", "简体中文"),
    EN("en", "English"),
    JA("ja", "日本語");

    companion object {
        fun fromSystemOrCode(code: String?): AppLanguage {
            if (!code.isNullOrBlank()) {
                entries.firstOrNull { it.code == code }?.let { return it }
            }
            val locale = Locale.getDefault()
            return when (locale.language) {
                "ja" -> JA
                "zh" -> if (locale.country.equals("CN", ignoreCase = true) || locale.script == "Hans") ZH_CN else ZH_TW
                "en" -> EN
                else -> ZH_TW
            }
        }
    }
}

data class DxStrings(
    val appName: String,
    val rootNav: String,
    val storageDevices: String,
    val internalStorage: String,
    val addCloudMount: String,
    val categoriesSection: String,
    val bookmarksSection: String,
    val toolboxSection: String,
    val storageAnalyzer: String,
    val webShare: String,
    val toggleHidden: String,
    val selectAll: String,
    val settingsTitle: String,
    val recycleBinTitle: String,
    val shizukuTitle: String,
    val showHiddenTitle: String,
    val darkThemeTitle: String,
    val darkBgStyleTitle: String,
    val darkBgOledBlack: String,
    val darkBgDarkGray: String,
    val darkBgBlackToGrayGlass: String,
    val languageTitle: String,
    val checkUpdate: String,
    val githubProject: String,
    val done: String
)

fun dxStringsFor(lang: AppLanguage): DxStrings = when (lang) {
    AppLanguage.ZH_TW -> DxStrings(
        appName = "DX檔案管理器",
        rootNav = "根目錄與導覽",
        storageDevices = "儲存裝置",
        internalStorage = "內部儲存空間",
        addCloudMount = "新增雲端 / 網路掛載",
        categoriesSection = "檔案分類 & 專區",
        bookmarksSection = "書籤",
        toolboxSection = "工具箱",
        storageAnalyzer = "DX 儲存空間分析器",
        webShare = "DX 區域網路網頁分享",
        toggleHidden = "切換顯示隱藏資料夾",
        selectAll = "全選",
        settingsTitle = "設定與關於我",
        recycleBinTitle = "啟用垃圾桶 (30 天自動清除)",
        shizukuTitle = "使用 Shizuku 靜默安裝 APK",
        showHiddenTitle = "顯示隱藏資料夾與檔案",
        darkThemeTitle = "深色主題 (Windows 黃黑風格)",
        darkBgStyleTitle = "深色背景底色",
        darkBgOledBlack = "全黑 (OLED 螢幕適用)",
        darkBgDarkGray = "灰黑底",
        darkBgBlackToGrayGlass = "黑至灰黑玻璃漸層",
        languageTitle = "介面語言 (Language)",
        checkUpdate = "檢查更新",
        githubProject = "GitHub 專案",
        done = "完成"
    )
    AppLanguage.ZH_CN -> DxStrings(
        appName = "DX文件管理器",
        rootNav = "根目录与导航",
        storageDevices = "存储设备",
        internalStorage = "内部存储空间",
        addCloudMount = "添加云盘 / 网络挂载",
        categoriesSection = "文件分类 & 专区",
        bookmarksSection = "书签",
        toolboxSection = "工具箱",
        storageAnalyzer = "DX 存储空间分析器",
        webShare = "DX 局域网网页分享",
        toggleHidden = "切换显示隐藏文件夹",
        selectAll = "全选",
        settingsTitle = "设置与关于",
        recycleBinTitle = "启用回收站 (30 天自动清理)",
        shizukuTitle = "使用 Shizuku 静默安装 APK",
        showHiddenTitle = "显示隐藏文件夹与文件",
        darkThemeTitle = "深色主题 (Windows 黄黑风格)",
        darkBgStyleTitle = "深色背景底色",
        darkBgOledBlack = "纯黑 (OLED 屏幕适用)",
        darkBgDarkGray = "灰黑底",
        darkBgBlackToGrayGlass = "黑至灰黑玻璃渐变",
        languageTitle = "界面语言 (Language)",
        checkUpdate = "检查更新",
        githubProject = "GitHub 项目",
        done = "完成"
    )
    AppLanguage.EN -> DxStrings(
        appName = "DX File Manager",
        rootNav = "Root & Navigation",
        storageDevices = "Storage Devices",
        internalStorage = "Internal Storage",
        addCloudMount = "Add Cloud / Network Mount",
        categoriesSection = "Categories & Tools",
        bookmarksSection = "Bookmarks",
        toolboxSection = "Toolbox",
        storageAnalyzer = "DX Storage Analyzer",
        webShare = "DX LAN WebShare",
        toggleHidden = "Toggle Hidden Folders",
        selectAll = "Select All",
        settingsTitle = "Settings & About",
        recycleBinTitle = "Enable Recycle Bin (30-day auto clean)",
        shizukuTitle = "Silent APK Install via Shizuku",
        showHiddenTitle = "Show Hidden Files & Folders",
        darkThemeTitle = "Dark Theme (Windows Yellow-Black)",
        darkBgStyleTitle = "Dark Background Style",
        darkBgOledBlack = "Pure Black (OLED)",
        darkBgDarkGray = "Dark Gray",
        darkBgBlackToGrayGlass = "Black-Gray Glass",
        languageTitle = "Language",
        checkUpdate = "Check Update",
        githubProject = "GitHub Repo",
        done = "Done"
    )
    AppLanguage.JA -> DxStrings(
        appName = "DXファイルマネージャー",
        rootNav = "ルート・ナビゲーション",
        storageDevices = "ストレージ",
        internalStorage = "内部ストレージ",
        addCloudMount = "クラウド / ネットワーク追加",
        categoriesSection = "カテゴリ・ツール",
        bookmarksSection = "ブックマーク",
        toolboxSection = "ツールボックス",
        storageAnalyzer = "DX ストレージ分析",
        webShare = "DX LAN Web共有",
        toggleHidden = "隠しフォルダ表示切替",
        selectAll = "すべて選択",
        settingsTitle = "設定とアプリ情報",
        recycleBinTitle = "ゴミ箱を有効にする (30日自動削除)",
        shizukuTitle = "Shizuku で APK をサイレントインストール",
        showHiddenTitle = "隠しファイル・フォルダを表示",
        darkThemeTitle = "ダークテーマ (Windows 黄黒スタイル)",
        darkBgStyleTitle = "ダーク背景スタイル",
        darkBgOledBlack = "完全な黒 (OLED 向け)",
        darkBgDarkGray = "ダークグレー",
        darkBgBlackToGrayGlass = "黒〜灰黒ガラス",
        languageTitle = "表示言語 (Language)",
        checkUpdate = "アップデート確認",
        githubProject = "GitHub プロジェクト",
        done = "完了"
    )
}
