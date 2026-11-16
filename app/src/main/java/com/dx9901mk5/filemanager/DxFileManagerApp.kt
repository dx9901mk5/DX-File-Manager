package com.dx9901mk5.filemanager

import android.app.Application
import com.dx9901mk5.filemanager.core.settings.AppPreferences
import com.dx9901mk5.filemanager.core.trash.TrashCleanupWorker
import com.dx9901mk5.filemanager.core.trash.TrashManager
import com.dx9901mk5.filemanager.core.vfs.VfsRegistry

class DxFileManagerApp : Application() {
    lateinit var preferences: AppPreferences
        private set
    lateinit var vfsRegistry: VfsRegistry
        private set
    lateinit var trashManager: TrashManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        vfsRegistry = VfsRegistry()
        trashManager = TrashManager(this)
        // 排程每日自動清理超過 30 天的垃圾桶項目
        TrashCleanupWorker.scheduleDaily(this)
    }
}
