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

package org.tobi29.amboss.systemd

import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.utils.io.filesystem.*
import org.tobi29.scapes.engine.utils.io.tag.structure

class SystemDKickstarter(channel: ControlPanelProtocol,
                         private val servers: FilePath,
                         service: String) {
    init {
        channel.addCommand("List") {
            channel.send("List", structure {
                list(servers) {
                    setList("Servers",
                            filter(::isDirectory).filter(::isNotHidden).map {
                                structure {
                                    setString("Name", it.fileName.toString())
                                }
                            }.toList())
                }
            })
        }
        channel.addCommand("Start") { payload ->
            payload.getString("Name")?.let { name ->
                if (!checkServer(name)) {
                    return@addCommand
                }
                AmbossSystemD.RUNTIME.exec(
                        arrayOf("systemctl", "--user", "start",
                                "$service@$name"))
            }
        }
        channel.addCommand("Stop") { payload ->
            payload.getString("Name")?.let { name ->
                if (!checkServer(name)) {
                    return@addCommand
                }
                AmbossSystemD.RUNTIME.exec(
                        arrayOf("systemctl", "--user", "stop",
                                "$service@$name"))
            }
        }
        channel.addCommand("Restart") { payload ->
            payload.getString("Name")?.let { name ->
                if (!checkServer(name)) {
                    return@addCommand
                }
                AmbossSystemD.RUNTIME.exec(
                        arrayOf("systemctl", "--user", "restart",
                                "$service@$name"))
            }
        }
    }

    private fun checkServer(name: String): Boolean {
        for (character in name) {
            if (!Character.isLetterOrDigit(character)) {
                return false
            }
        }
        val serverDir = servers.resolve(name)
        if (!isDirectory(serverDir) || isHidden(serverDir)) {
            return false
        }
        return true
    }
}