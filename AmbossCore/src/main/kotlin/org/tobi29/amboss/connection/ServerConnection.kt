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

import org.tobi29.amboss.AmbossServer
import org.tobi29.scapes.engine.server.*
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.task.TaskExecutor
import java.io.IOException
import java.nio.channels.SocketChannel
import java.util.*

class ServerConnection(val amboss: AmbossServer,
                       taskExecutor: TaskExecutor,
                       connectionHeader: ByteArray,
                       ssl: SSLHandle,
                       private val adminsStructure: TagStructure) : AbstractServerConnection(
        taskExecutor, connectionHeader, ssl) {
    val events = amboss.events

    override fun accept(channel: SocketChannel): String? {
        return null
    }

    override fun newConnection(worker: ConnectionWorker,
                               channel: PacketBundleChannel,
                               id: Byte): Connection? {
        when (id) {
            1.toByte() -> {
                val controlChannel = WrapperConnection(worker, this,
                        channel) { id, mode ->
                    val uuid = try {
                        UUID.fromString(id)
                    } catch (e: IllegalArgumentException) {
                        throw IOException(e)
                    }
                    val server = amboss.serverKeyGet(
                            uuid) ?: return@WrapperConnection null
                    ControlPanelProtocol.keyPairAuthentication(mode,
                            server.second)
                }
                return controlChannel
            }
            2.toByte() -> {
                val controlChannel = ShellConnection(worker, this,
                        channel) { id, mode, salt ->
                    val password = adminsStructure.getString(
                            id) ?: return@ShellConnection null
                    ControlPanelProtocol.passwordAuthentication(mode, salt,
                            password)
                }
                amboss.plugins.initShell(controlChannel)
                return controlChannel
            }
            3.toByte() -> {
                val controlChannel = KickstarterConnection(worker, this,
                        channel) { id, mode ->
                    val uuid = try {
                        UUID.fromString(id)
                    } catch (e: IllegalArgumentException) {
                        throw IOException(e)
                    }
                    val server = amboss.serverKeyGet(
                            uuid) ?: return@KickstarterConnection null
                    ControlPanelProtocol.keyPairAuthentication(mode,
                            server.second)
                }
                return controlChannel
            }
        }
        return null
    }
}
