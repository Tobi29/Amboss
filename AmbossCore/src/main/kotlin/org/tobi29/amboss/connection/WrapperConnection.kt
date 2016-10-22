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
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.server.PacketBundleChannel
import java.io.IOException
import javax.crypto.Cipher

class WrapperConnection(val server: ServerConnection,
                        channel: PacketBundleChannel,
                        authentication: (String, Int) -> Cipher?) : ControlPanelProtocol(
        channel, server.events, authentication) {
    private var init = false

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
    }
}
