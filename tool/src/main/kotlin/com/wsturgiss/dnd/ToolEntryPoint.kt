package com.wsturgiss.dnd

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec
import com.wsturgiss.dnd.model.Store
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    // Everything is local: nothing to set up, nothing pushed.
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) = Unit

    override suspend fun onPushNotification(data: ByteArray) = Unit

    /** The save file, in the Tool Manager: how light-web syncs it, and what LightOS backup can see. */
    override fun getToolManagerManifest() = ClientToolManifest(
        title = "D&D",
        roots = listOf(
            ClientLeafNode(
                FileBrowserSpec(label = "Characters", path = Store.SHARED_DIR, headerText = "Your characters and roll log."),
                canBeBackedUp = true,
            ),
        ),
    )

    /** A sync replaced the save file: pick it up, and stop the next save writing over it. */
    override suspend fun onToolManagerDataUpdate() = Store.reload()
}
