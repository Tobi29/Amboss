/*
 * Copyright 2012-2016 Tobi29
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tobi29.amboss.connection

import org.tobi29.amboss.plugin.event.LogEvent
import org.tobi29.scapes.engine.server.ConnectionWorker
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.server.PacketBundleChannel
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.structure
import java.io.IOException
import java.util.*
import javax.crypto.Cipher

class WrapperConnection(worker: ConnectionWorker,
                        val server: ServerConnection,
                        channel: PacketBundleChannel,
                        authentication: (String, Int) -> Cipher?) : ControlPanelProtocol(
        worker, channel, server.events, authentication) {
    val players: List<Player>
        get() = Collections.unmodifiableList(playerList)
    private var init = false
    private var playerList = emptyList<Player>()

    init {
        addCommand("Wrapper-Init") { payload ->
            if (init) {
                throw IOException("Wrapper-Init received twice")
            }
            init = true

            addCommand("Log") { payload ->
                payload.getString("Message")?.let { message ->
                    events.fire(LogEvent(this, message))
                }
            }

            server.amboss.serversAdd(this)
            server.amboss.plugins.initServer(this, payload)
        }
        addCommand("Players-List") { payload ->
            payload.getList("Players")?.let { list ->
                playerList = list.mapNotNull {
                    (it as? TagStructure)?.let(::Player)
                }
            }
        }
    }
}

data class Player(val name: String)

fun Player(tagStructure: TagStructure): Player? {
    return tagStructure.getString("Name")?.let { name ->
        return Player(name)
    }
}

fun Player.write(): TagStructure {
    return structure {
        setString("Name", name)
    }
}
