package com.wsturgiss.dnd

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    // Everything is local: nothing to set up, nothing pushed.
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) = Unit

    override suspend fun onPushNotification(data: ByteArray) = Unit
}
