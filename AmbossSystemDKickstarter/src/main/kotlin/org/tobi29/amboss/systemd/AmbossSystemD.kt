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
    options.addOption("s", "servers", true, "Servers directory")
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
    val serversOption = commandLine.getOptionValue('s')
    val servers = if (serversOption == null) {
        path(System.getProperty("user.dir")).resolve("Servers")
    } else {
        path(serversOption)
    }.toAbsolutePath().normalize()
    if (!exists(servers)) {
        createDirectories(servers)
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
    val service = tagStructure.getString("Service") ?: run {
        System.err.println("No service in config!")
        System.exit(1)
        return
    }

    // Set up task executor
    val taskExecutor = TaskExecutor(object : Crashable {
        override fun crash(e: Throwable) {
            AmbossSystemD.logger.error(e) { "A fatal exception occurred" }
            exitLater(1)
        }
    }, "Wrapper")

    // Set up connection worker
    val ssl = SSLProvider.sslHandle {
        if (ignoreCertificate) {
            AmbossSystemD.logger.warn("Invalid core certificate!")
            true
        } else {
            false
        }
    }
    val connection = ConnectionManager(taskExecutor)
    connection.workers(1)

    AmbossSystemD.run(address, uuid, privateKey, servers, service, connection,
            taskExecutor, ssl)
}

private fun loadConfig(path: FilePath): TagStructure {
    if (exists(path)) {
        return read(path) { TagStructureJSON.read(it) }
    }
    val tagStructure = structure {
        setStructure("Connection") {
            setString("Address", "localhost")
            setNumber("Port", 26555)
            setBoolean("IgnoreCertificate", false)
        }
        setString("UUID", "")
        setString("PrivateKey", "")
        setString("Service", "mcserver")
    }
    write(path) { TagStructureJSON.write(tagStructure, it) }
    return tagStructure
}

object AmbossSystemD : KLogging() {
    val RUNTIME = Runtime.getRuntime()
    val CONNECTION_HEADER = byteArrayOf('A'.toByte(), 'm'.toByte(),
            'b'.toByte(), 'o'.toByte(), 's'.toByte(), 's'.toByte())

    fun run(address: RemoteAddress,
            uuid: UUID,
            privateKey: PrivateKey,
            servers: FilePath,
            service: String,
            connection: ConnectionManager,
            taskExecutor: TaskExecutor,
            ssl: SSLHandle) {
        taskExecutor.start()
        RUNTIME.addShutdownHook(Thread {
            connection.stop()
            taskExecutor.shutdown()
        })
        connect(address, uuid, privateKey, servers, service, connection,
                taskExecutor, ssl)
    }

    fun connect(address: RemoteAddress,
                uuid: UUID,
                privateKey: PrivateKey,
                servers: FilePath,
                service: String,
                connection: ConnectionManager,
                taskExecutor: TaskExecutor,
                ssl: SSLHandle) {
        logger.info { "Connecting..." }
        connection.addOutConnection(address, { e ->
            logger.error { "Failed to connect: $e" }
            taskExecutor.addTaskOnce({
                connect(address, uuid, privateKey, servers, service,
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
                SystemDKickstarter(channel, servers, service)
                channel.openHook { logger.info { "Connected!" } }
                channel.closeHook {
                    logger.info { "Disconnected!" }
                    taskExecutor.addTaskOnce({
                        connect(address, uuid, privateKey, servers, service,
                                connection, taskExecutor, ssl)
                    }, "Reconnect", 5000)
                }
                channel
            }
        }
    }
}
