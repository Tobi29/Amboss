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

package org.tobi29.amboss.wrapper

import java8.util.stream.Collectors
import org.tobi29.amboss.wrapper.logparse.LogParser
import org.tobi29.scapes.engine.utils.io.asArray
import org.tobi29.scapes.engine.utils.io.filesystem.*
import org.tobi29.scapes.engine.utils.io.process
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.getListString
import org.tobi29.scapes.engine.utils.io.tag.structure
import org.tobi29.scapes.engine.utils.mapNotNull
import org.tobi29.scapes.engine.utils.stream
import java.io.IOException
import java.util.regex.Pattern

class ServerSlave(logParser: LogParser,
                  execute: (String) -> Unit,
                  instance: WrapperInstance,
                  data: FilePath) {
    init {
        logParser.addProcessor(".*") { matcher ->
            val log = matcher.group()
            val payload = TagStructure()
            payload.setString("Message", log)
            instance.send("Log", payload)
        }
        logParser.addProcessor("$PLAYER_NAME (.*)") { matcher ->
            val name = matcher.group(2)
            val message = matcher.group(4)
            val payload = TagStructure()
            payload.setString("Name", name)
            payload.setString("Message", message)
            instance.send("Chat", payload)
        }
        logParser.addProcessor("$PLAYER_NAME joined the game") { matcher ->
            instance.send("Players-Join", structure {
                setString("Name", matcher.group(2))
            })
            execute("list")
        }
        logParser.addProcessor("$PLAYER_NAME left the game") { matcher ->
            instance.send("Players-Leave", structure {
                setString("Name", matcher.group(2))
            })
            execute("list")
        }
        logParser.addProcessor("There are [0-9]+/[0-9]+ players online:",
                ".*") { matchers ->
            val payload = TagStructure()
            val players = matchers[1].group()
            if (players.isEmpty()) {
                payload.setList("Players", emptyList())
            } else {
                val list = stream(*PLAYERS_SPLIT.split(players)).map { name ->
                    structure { setString("Name", name) }
                }.collect(Collectors.toList<TagStructure>())
                payload.setList("Players", list)
            }
            instance.send("Players-List", payload)
        }
        instance.onConnect {
            addCommand("Players-List") {
                execute("list")
            }
            addCommand("Command") { payload ->
                payload.getString("Command")?.let { execute(it) }
                payload.getListString("Commands") { execute(it) }
            }
            addCommand("Directory-Access") { payload ->
                payload.getString("Request")?.let { request ->
                    payload.getString("Path")?.mapNotNull {
                        data.resolve(it).toAbsolutePath().normalize()
                    }?.let { path ->
                        try {
                            send(request, structure {
                                checkAccess(path, data)
                                setStructure("Files", saveDirectory(path, data))
                            })
                        } catch(e: IOException) {
                            send(request, structure {
                                setString("Error", e.toString())
                            })
                        }
                    }
                }
            }
        }
    }

    private fun saveDirectory(path: FilePath,
                              container: FilePath): TagStructure {
        return structure {
            stream(path) {
                it.map { it.toAbsolutePath().normalize() }.forEach { entry ->
                    checkAccess(entry, container)
                    val name = entry.fileName.toString()
                    if (isDirectory(entry)) {
                        setStructure(name, saveDirectory(entry, container))
                    } else if (isRegularFile(entry)) {
                        val contents = read(entry) { process(it, asArray()) }
                        setByteArray(name, *contents)
                    }
                }
            }
        }
    }

    private fun checkAccess(path: FilePath,
                            container: FilePath) {
        // Last defense against allowing core to access all files on server
        // This should be checked against security breaches!
        // TODO: Search for potential security problems
        if (!path.startsWith(container)) {
            // Do not be descriptive to never leak information about outside files
            throw IOException("Access denied")
        }
    }

    companion object {
        val PLAYERS_SPLIT: Pattern = Pattern.compile(", ")
        val PLAYER_NAME = "([\\w\\[\\]\\- ]|§[0-9a-fr])*?(\\w+)(§[0-9a-fr])*?"
    }
}
