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

package org.tobi29.amboss.shell

import mu.KLogging
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.getUUID
import org.tobi29.scapes.engine.utils.io.tag.setUUID
import org.tobi29.scapes.engine.utils.io.tag.structure
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ShellCommands(channel: ControlPanelProtocol) {
    private val commands = ConcurrentHashMap<String, (Array<String>) -> Unit>()

    init {
        channel.addCommand("Message") { payload ->
            payload.getString("Message")?.let(::println)
        }

        // Listing online servers
        command("servers-list", {}) { commandLine ->
            val payload = TagStructure()
            channel.send("Servers-List", payload)
        }

        // Listing online kickstarters
        command("kickstarters-list", {}) { commandLine ->
            val payload = TagStructure()
            channel.send("Kickstarters-List", payload)
        }

        // Receiving log from server
        command("server-listen", { }) { commandLine ->
            commandLine.args.forEach { uuidStr ->
                val uuid = try {
                    UUID.fromString(uuidStr)
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid UUID given: ${e.message}" }
                    return@command
                }
                channel.send("Server-Listen", structure {
                    setUUID("UUID", uuid)
                })
            }
        }

        // Listing servers of kickstarter
        command("kickstarter-servers-list", {
            addOption("k", "kickstarter", true, "Kickstarter UUID")
        }) { commandLine ->
            val uuidStr = commandLine.getOptionValue('k')
            if (uuidStr == null) {
                logger.warn { "No Kickstarter UUID given" }
                return@command
            }
            val uuid = try {
                UUID.fromString(uuidStr)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Invalid Kickstarter UUID given: ${e.message}" }
                return@command
            }
            channel.send("Kickstarter-Servers-List",
                    structure { setUUID("Kickstarter", uuid) })
        }
        channel.addCommand("Kickstarter-Servers-List") { payload ->
            val output = StringBuilder(1024)
            payload.getList("Servers")?.forEach { serverStructure ->
                serverStructure.getString("Name")?.let { name ->
                    output.append("$name\n")
                }
            }
            println(output.toString())
        }

        command("kickstarter-servers-start", {
            addOption("k", "kickstarter", true, "Kickstarter UUID")
        }) { commandLine ->
            val uuidStr = commandLine.getOptionValue('k')
            if (uuidStr == null) {
                logger.warn { "No Kickstarter UUID given" }
                return@command
            }
            val uuid = try {
                UUID.fromString(uuidStr)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Invalid Kickstarter UUID given: ${e.message}" }
                return@command
            }
            commandLine.args.forEach { name ->
                channel.send("Kickstarter-Servers-Start",
                        structure {
                            setUUID("Kickstarter", uuid)
                            setString("Name", name)
                        })
            }
        }
        command("kickstarter-servers-stop", {
            addOption("k", "kickstarter", true, "Kickstarter UUID")
        }) { commandLine ->
            val uuidStr = commandLine.getOptionValue('k')
            if (uuidStr == null) {
                logger.warn { "No Kickstarter UUID given" }
                return@command
            }
            val uuid = try {
                UUID.fromString(uuidStr)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Invalid Kickstarter UUID given: ${e.message}" }
                return@command
            }
            commandLine.args.forEach { name ->
                channel.send("Kickstarter-Servers-Stop",
                        structure {
                            setUUID("Kickstarter", uuid)
                            setString("Name", name)
                        })
            }
        }
        command("kickstarter-servers-restart", {
            addOption("k", "kickstarter", true, "Kickstarter UUID")
        }) { commandLine ->
            val uuidStr = commandLine.getOptionValue('k')
            if (uuidStr == null) {
                logger.warn { "No Kickstarter UUID given" }
                return@command
            }
            val uuid = try {
                UUID.fromString(uuidStr)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Invalid Kickstarter UUID given: ${e.message}" }
                return@command
            }
            commandLine.args.forEach { name ->
                channel.send("Kickstarter-Servers-Restart",
                        structure {
                            setUUID("Kickstarter", uuid)
                            setString("Name", name)
                        })
            }
        }

        // Adding server keys
        command("server-keys-add", {}) { commandLine ->
            commandLine.args.forEach { name ->
                val payload = TagStructure()
                payload.setString("Name", name)
                channel.send("Server-Keys-Add", payload)
            }
        }
        channel.addCommand("Server-Keys-Add") { payload ->
            payload.getBoolean("Success")?.let { success ->
                if (success) {
                    logger.info { "Added key successfully" }
                    payload.getUUID("UUID")?.let {
                        logger.info { "UUID: $it" }
                    }
                    payload.getString("PrivateKey")?.let {
                        logger.info { "Private Key: $it" }
                    }
                } else {
                    logger.warn { "Failed to add key" }
                    payload.getString("Error")?.let {
                        logger.warn { it }
                    }
                }
            }
        }

        // Removing server keys
        command("server-keys-remove", {}) { commandLine ->
            commandLine.args.forEach { uuidStr ->
                val uuid = try {
                    UUID.fromString(uuidStr)
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Invalid UUID given: ${e.message}" }
                    return@command
                }
                channel.send("Server-Keys-Remove", structure {
                    setUUID("UUID", uuid)
                })
            }
        }
        channel.addCommand("Server-Keys-Remove") { payload ->
            payload.getBoolean("Success")?.let { success ->
                if (success) {
                    logger.info { "Removed key successfully" }
                } else {
                    logger.warn { "Failed to remove key" }
                    payload.getString("Error")?.let {
                        logger.warn { it }
                    }
                }
            }
        }

        // Listing server keys
        command("server-keys-list", {}) { commandLine ->
            val payload = TagStructure()
            channel.send("Server-Keys-List", payload)
        }
    }

    fun execute(vararg command: String) {
        if (command.size == 0) {
            return
        }
        val sink = commands[command[0]] ?: run {
            logger.warn { "Unknown command: ${command[0]}" }
            return
        }
        sink(Array(command.size - 1) { command[it + 1] })
    }

    private fun command(name: String,
                        configure: Options.() -> Unit,
                        execute: (CommandLine) -> Unit) {
        commands.put(name) { args ->
            val options = Options()
            configure(options)
            options.addOption("h", "help", false, "Print this text and exit")
            val parser = DefaultParser()
            val commandLine: CommandLine
            try {
                commandLine = parser.parse(options, args)
            } catch (e: ParseException) {
                logger.warn { "Failed to parse command: ${e.message}" }
                return@put
            }
            execute(commandLine)
        }
    }

    companion object : KLogging()
}
