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

package org.tobi29.amboss.plugin.shell

import org.tobi29.amboss.AmbossServer
import org.tobi29.amboss.connection.sendMessage
import org.tobi29.amboss.plugin.Plugin
import org.tobi29.amboss.plugin.event.LogEvent
import org.tobi29.amboss.plugin.test.TestPlugin
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.utils.io.tag.getUUID
import org.tobi29.scapes.engine.utils.io.tag.setUUID
import org.tobi29.scapes.engine.utils.io.tag.structure
import org.tobi29.scapes.engine.utils.mapNotNull
import org.tobi29.scapes.engine.utils.writePrivate
import java.security.spec.InvalidKeySpecException

class ShellPlugin(amboss: AmbossServer) : Plugin(amboss) {
    override fun initShell(channel: ControlPanelProtocol) {
        channel.addCommand("Servers-List") { payload ->
            val output = StringBuilder(1024)
            amboss.serversList().forEach {
                output.append("${it.first}: ${it.second}\n")
            }
            channel.sendMessage(output.toString())
        }
        channel.addCommand("Kickstarters-List") { payload ->
            val output = StringBuilder(1024)
            amboss.kickstartersList().forEach {
                output.append("${it.first}: ${it.second}\n")
            }
            channel.sendMessage(output.toString())
        }
        channel.addCommand("Server-Keys-Add") { payload ->
            payload.getString("Name")?.let { name ->
                amboss.serverKeysAdd(name) { uuid, keyPair ->
                    try {
                        val privateKey = writePrivate(keyPair.private)
                        channel.send("Server-Keys-Add", structure {
                            setBoolean("Success", true)
                            setUUID("UUID", uuid)
                            setString("PrivateKey", privateKey)
                        })
                    } catch (e: InvalidKeySpecException) {
                        TestPlugin.logger.warn { "Failed to send new server message: $e" }
                    }
                }
            }
        }
        channel.addCommand("Server-Keys-Remove") { payload ->
            payload.getUUID("UUID")?.let { uuid ->
                if (amboss.serverKeysRemove(uuid)) {
                    channel.send("Server-Keys-Remove", structure {
                        setBoolean("Success", true)
                    })
                } else {
                    channel.send("Server-Keys-Remove", structure {
                        setBoolean("Success", false)
                        setString("Error", "Unknown server: $uuid")
                    })
                }
            }
        }
        channel.addCommand("Server-Keys-List") { payload ->
            val output = StringBuilder(1024)
            amboss.serverKeysList().forEach {
                output.append("${it.first}: ${it.second}\n")
            }
            channel.sendMessage(output.toString())
        }
        channel.addCommand("Kickstarter-Servers-List") { payload ->
            payload.getUUID("Kickstarter")?.mapNotNull {
                amboss.kickstartersGet(it)
            }?.let { kickstarter ->
                kickstarter.commandHook("List") { payload ->
                    channel.send("Kickstarter-Servers-List", payload)
                }
                kickstarter.send("List", structure {})
            }
        }
        channel.addCommand("Kickstarter-Servers-Start") { payload ->
            payload.getUUID("Kickstarter")?.mapNotNull {
                amboss.kickstartersGet(it)
            }?.let { kickstarter ->
                kickstarter.send("Start", payload)
            }
        }
        channel.addCommand("Kickstarter-Servers-Stop") { payload ->
            payload.getUUID("Kickstarter")?.mapNotNull {
                amboss.kickstartersGet(it)
            }?.let { kickstarter ->
                kickstarter.send("Stop", payload)
            }
        }
        channel.addCommand("Kickstarter-Servers-Restart") { payload ->
            payload.getUUID("Kickstarter")?.mapNotNull {
                amboss.kickstartersGet(it)
            }?.let { kickstarter ->
                kickstarter.send("Restart", payload)
            }
        }
        channel.addCommand("Server-Listen") { payload ->
            payload.getUUID("UUID")?.mapNotNull {
                amboss.serversGet(it)
            }?.let { server ->
                server.events.listener<LogEvent>(channel) { event ->
                    channel.sendMessage(event.message)
                }
            }
        }
    }
}
