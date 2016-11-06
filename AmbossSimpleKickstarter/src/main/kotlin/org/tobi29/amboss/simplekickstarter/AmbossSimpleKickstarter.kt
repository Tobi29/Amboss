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

package org.tobi29.amboss.simplekickstarter

import mu.KLogging
import org.apache.commons.cli.*
import org.tobi29.scapes.engine.server.*
import org.tobi29.scapes.engine.utils.Crashable
import org.tobi29.scapes.engine.utils.exitLater
import org.tobi29.scapes.engine.utils.io.filesystem.*
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.getUUID
import org.tobi29.scapes.engine.utils.io.tag.json.TagStructureJSON
import org.tobi29.scapes.engine.utils.io.tag.setStructure
import org.tobi29.scapes.engine.utils.io.tag.structure
import org.tobi29.scapes.engine.utils.readPrivate
import org.tobi29.scapes.engine.utils.task.TaskExecutor
import org.tobi29.scapes.engine.utils.task.start
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.util.*

fun main(args: Array<String>) {
    val options = Options()
    options.addOption("h", "help", false, "Print this text and exit")
    options.addOption("v", "version", false, "Print version and exit")
    options.addOption("c", "config", true, "Config directory")
    options.addOption("s", "server", true, "Server directory")
    val parser = DefaultParser()
    val commandLine: CommandLine
    try {
        commandLine = parser.parse(options, args)
    } catch (e: ParseException) {
        System.err.println(e.message)
        System.exit(255)
        return
    }

    if (commandLine.hasOption('h')) {
        val helpFormatter = HelpFormatter()
        helpFormatter.printHelp("", options)
        System.exit(0)
        return
    }
    if (commandLine.hasOption('v')) {
        println("0.Something.Something_Whatever")
        System.exit(0)
        return
    }
    val configOption = commandLine.getOptionValue('c')
    val config = if (configOption == null) {
        path(System.getProperty("user.dir")).resolve("Config")
    } else {
        path(configOption)
    }.toAbsolutePath().normalize()
    if (!exists(config)) {
        createDirectories(config)
    }
    val serverOption = commandLine.getOptionValue('s')
    val serverDir = if (serverOption == null) {
        path(System.getProperty("user.dir")).resolve("Server")
    } else {
        path(serverOption)
    }.toAbsolutePath().normalize()
    if (!exists(serverDir)) {
        createDirectories(serverDir)
    }


    // Load config
    val tagStructure = loadConfig(config.resolve("Server.json"))
    val connectionStructure = tagStructure.structure("Connection")
    val address = RemoteAddress(connectionStructure)
    val ignoreCertificate = connectionStructure.getBoolean(
            "IgnoreCertificate") ?: false
    val uuid = tagStructure.getUUID("UUID") ?: run {
        System.err.println("No valid UUID in config!")
        System.exit(1)
        return
    }
    val privateKeyStr = tagStructure.getString("PrivateKey") ?: run {
        System.err.println("No private key in config!")
        System.exit(1)
        return
    }
    val privateKey = try {
        readPrivate(privateKeyStr)
    } catch (e: InvalidKeySpecException) {
        System.err.println("Error decoding private key: $e")
        System.exit(1)
        return
    }
    val serverName = tagStructure.getString("Name") ?: run {
        System.err.println("No name in config!")
        System.exit(1)
        return
    }
    val command = tagStructure.getString("Command") ?: run {
        System.err.println("No command in config!")
        System.exit(1)
        return
    }

    // Set up server
    val server = MCServerProcess(command, serverDir)

    // Set up task executor
    val taskExecutor = TaskExecutor(object : Crashable {
        override fun crash(e: Throwable) {
            AmbossSimpleKickstarter.logger.error(
                    e) { "A fatal exception occurred" }
            exitLater(1)
        }
    }, "Wrapper")

    // Set up connection worker
    val ssl = SSLProvider.sslHandle {
        if (ignoreCertificate) {
            AmbossSimpleKickstarter.logger.warn("Invalid core certificate!")
            true
        } else {
            false
        }
    }
    val connection = ConnectionManager(taskExecutor)
    connection.workers(1)

    AmbossSimpleKickstarter.run(address, uuid, privateKey, serverName, server,
            connection, taskExecutor, ssl)
}

private fun loadConfig(path: FilePath): TagStructure {
    if (exists(path)) {
        return read(path) { TagStructureJSON.Companion.read(it) }
    }
    val tagStructure = structure {
        setStructure("Connection") {
            setString("Address", "localhost")
            setNumber("Port", 26555)
            setBoolean("IgnoreCertificate", false)
        }
        setString("UUID", "")
        setString("PrivateKey", "")
        setString("Command",
                "java -cp minecraft_server.jar:MCWrapper.jar org.tobi29.amboss.wrapper.AmbossWrapperKt")
        setString("Name", "Server")
    }
    write(path) { TagStructureJSON.Companion.write(tagStructure, it) }
    return tagStructure
}

object AmbossSimpleKickstarter : KLogging() {
    val RUNTIME = Runtime.getRuntime()
    val CONNECTION_HEADER = byteArrayOf('A'.toByte(), 'm'.toByte(),
            'b'.toByte(), 'o'.toByte(), 's'.toByte(), 's'.toByte())

    fun run(address: RemoteAddress,
            uuid: UUID,
            privateKey: PrivateKey,
            serverName: String,
            server: MCServerProcess,
            connection: org.tobi29.scapes.engine.server.ConnectionManager,
            taskExecutor: TaskExecutor,
            ssl: SSLHandle) {
        taskExecutor.start()
        RUNTIME.addShutdownHook(Thread {
            connection.stop()
            taskExecutor.shutdown()
        })
        connect(address, uuid, privateKey, serverName, server, connection,
                taskExecutor, ssl)
    }

    fun connect(address: RemoteAddress,
                uuid: UUID,
                privateKey: PrivateKey,
                serverName: String,
                server: MCServerProcess,
                connection: ConnectionManager,
                taskExecutor: TaskExecutor,
                ssl: SSLHandle) {
        logger.info { "Connecting..." }
        connection.addOutConnection(address, { e ->
            logger.error { "Failed to connect: $e" }
            taskExecutor.addTaskOnce({
                connect(address, uuid, privateKey, serverName, server,
                        connection, taskExecutor, ssl)
            }, "Reconnect", 20000)
        }) { worker, socketChannel ->
            val bundleChannel = PacketBundleChannel(address, socketChannel,
                    taskExecutor, ssl, true)
            val output = bundleChannel.outputStream
            output.put(CONNECTION_HEADER)
            output.put(3)
            bundleChannel.queueBundle()
            worker.addConnection {
                val channel = ControlPanelProtocol(worker, bundleChannel, null,
                        uuid.toString(),
                        ControlPanelProtocol.keyPairAuthentication(privateKey))
                SimpleKickstarter(channel, serverName, server)
                channel.openHook { logger.info { "Connected!" } }
                channel.closeHook {
                    logger.info { "Disconnected!" }
                    taskExecutor.addTaskOnce({
                        connect(address, uuid, privateKey, serverName, server,
                                connection, taskExecutor, ssl)
                    }, "Reconnect", 5000)
                }
                channel
            }
        }
    }
}
