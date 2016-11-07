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

package org.tobi29.amboss.plugin.clockgenerator

import com.tobi29.minecraft.utils.clockgenerator.ProjectException
import com.tobi29.minecraft.utils.clockgenerator.generator.GeneratorException
import com.tobi29.minecraft.utils.clockgenerator.parser.Parser
import com.tobi29.minecraft.utils.clockgenerator.parser.ParserException
import mu.KLogging
import org.tobi29.amboss.AmbossServer
import org.tobi29.amboss.connection.WrapperConnection
import org.tobi29.amboss.plugin.Plugin
import org.tobi29.amboss.plugin.event.ChatEvent
import org.tobi29.amboss.util.MCColor
import org.tobi29.amboss.util.TellrawString
import org.tobi29.amboss.util.block.writeBlocks
import org.tobi29.amboss.util.tellraw
import org.tobi29.amboss.util.tellrawMessage
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.setList
import org.tobi29.scapes.engine.utils.io.tag.structure

class ClockGeneratorPlugin(amboss: AmbossServer) : Plugin(amboss) {
    val TELLRAW_PREFIX = TellrawString("Server Lagger 9000", MCColor.dark_red)

    override fun initServer(wrapper: WrapperConnection,
                            configStructure: TagStructure) {
        val permissions = configStructure.structure("Permissions")
        wrapper.addCommand("ClockGenerator-Generate") { payload ->
            payload.getString("Error")?.let { error ->
                wrapper.send("Command", structure {
                    setString("Command", tellraw("@a",
                            tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                    "Failed to retrieve files: $error"))))
                })
                return@addCommand
            }
            payload.getStructure("Files")?.let { files ->
                amboss.taskExecutor.runTask({
                    generate(wrapper, files)
                }, "ClockGenerator-Generate")
            }
        }
        wrapper.events.listener<ChatEvent>(this, 10) { event ->
            if (event.muted) {
                return@listener
            }
            if (!(permissions.getStructure(event.name)?.getBoolean(
                    "Clock-Generator") ?: false)) {
                return@listener
            }
            if (event.message == "!make redstone") {
                event.muted = true
                wrapper.send("Directory-Access", structure {
                    setString("Request", "ClockGenerator-Generate")
                    setString("Path", "Redstone")
                })
            }
        }
    }

    private fun generate(channel: ControlPanelProtocol,
                         tagStructure: TagStructure) {
        try {
            val project = Parser.parse(tagStructure)

            val generator = project.createGenerator()
            project.generate(generator)

            val blocks = generator.generate()

            channel.send("Command", structure {
                setList("Commands") {
                    writeBlocks(project.start, blocks) { add(it) }
                }
            })
        } catch (e: ProjectException) {
            channel.send("Command", structure {
                setString("Command", tellraw("@a",
                        tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                "Failed to initialize project: ${e.message}"))))
            })
        } catch (e: ParserException) {
            channel.send("Command", structure {
                setString("Command", tellraw("@a",
                        tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                "Failed to parse project: ${e.message}"))))
            })
        } catch (e: GeneratorException) {
            channel.send("Command", structure {
                setString("Command", tellraw("@a",
                        tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                "Failed to generate project: ${e.message}"))))
            })
        }
    }

    companion object : KLogging()
}
