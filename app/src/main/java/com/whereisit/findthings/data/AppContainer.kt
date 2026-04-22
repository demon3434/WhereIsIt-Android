package com.whereisit.findthings.data

import android.content.Context
import com.whereisit.findthings.data.network.ApiFactory
import com.whereisit.findthings.data.network.LanDiscovery
import com.whereisit.findthings.data.network.ServiceAutoDiscovery
import com.whereisit.findthings.data.repository.ItemRepository
import com.whereisit.findthings.data.repository.SessionRepository
import com.whereisit.findthings.data.voice.VoiceSearchRepository

class AppContainer(context: Context) {
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
        appContext = context.applicationContext,
        apiFactory = apiFactory,
        sessionRepository = sessionRepository
    )
}
