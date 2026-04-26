package com.whereisit.findthings.data

import android.content.Context
import com.whereisit.findthings.data.network.ApiFactory
import com.whereisit.findthings.data.network.LanDiscovery
import com.whereisit.findthings.data.network.ServiceAutoDiscovery
import com.whereisit.findthings.data.repository.ItemRepository
import com.whereisit.findthings.data.repository.SessionRepository
import com.whereisit.findthings.data.voice.VoiceSearchRepository
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sessionRepository = SessionRepository(context)
    val lanDiscovery = LanDiscovery(context)
    val serviceAutoDiscovery = ServiceAutoDiscovery(context, sessionRepository, lanDiscovery)
    private val apiFactory = ApiFactory()
    val itemRepository = ItemRepository(
        apiFactory = apiFactory,
        sessionRepository = sessionRepository,
        contentResolver = context.contentResolver
    )
    val voiceSearchRepository = VoiceSearchRepository(
        appContext = appContext,
        apiFactory = apiFactory,
        sessionRepository = sessionRepository
    )

    suspend fun logout(clearCachedFiles: Boolean = true) {
        voiceSearchRepository.cancel()
        itemRepository.clearRuntimeAuth()
        sessionRepository.clearSessionState()
        apiFactory.clearCache()

        if (clearCachedFiles) {
            clearAppCaches()
        }
    }

    private fun clearAppCaches() {
        clearDirectoryContents(appContext.cacheDir)
        appContext.externalCacheDir?.let(::clearDirectoryContents)
    }

    private fun clearDirectoryContents(directory: File?) {
        directory?.listFiles()?.forEach { child ->
            runCatching {
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            }
        }
    }
}
