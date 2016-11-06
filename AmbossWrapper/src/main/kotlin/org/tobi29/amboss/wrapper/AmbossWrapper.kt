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

import org.apache.commons.cli.*
import org.tobi29.amboss.wrapper.logparse.LogParser
import org.tobi29.amboss.wrapper.logparse.LogReader
import org.tobi29.scapes.engine.server.*
import org.tobi29.scapes.engine.utils.Crashable
import org.tobi29.scapes.engine.utils.io.filesystem.*
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.getUUID
import org.tobi29.scapes.engine.utils.io.tag.json.TagStructureJSON
import org.tobi29.scapes.engine.utils.io.tag.setStructure
import org.tobi29.scapes.engine.utils.io.tag.structure
import org.tobi29.scapes.engine.utils.readPrivate
import org.tobi29.scapes.engine.utils.task.TaskExecutor
import org.tobi29.scapes.engine.utils.task.start
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.util.*

val stdout = System.out
val INPUT_BUFFER = 1024 shl 4

fun main(args: Array<String>) {
    val options = Options()
    options.addOption("h", "help", false, "Print this text and exit")
    options.addOption("v", "version", false, "Print version and exit")
    options.addOption("m", "main", true, "Main class")
    options.addOption("c", "config", true, "Config directory")
    options.addOption("d", "data", true, "Data directory")
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
    val mainClass = commandLine.getOptionValue('m',
            "net.minecraft.server.MinecraftServer")
    val configOption = commandLine.getOptionValue('c')
    val config = if (configOption == null) {
        path(System.getProperty("user.dir")).resolve("Config")
    } else {
        path(configOption)
    }.toAbsolutePath().normalize()
    if (!exists(config)) {
        createDirectories(config)
    }
    val dataOption = commandLine.getOptionValue('d')
    val data = if (dataOption == null) {
        path(System.getProperty("user.dir")).resolve("Data")
    } else {
        path(dataOption)
    }.toAbsolutePath().normalize()
    if (!exists(data)) {
        createDirectories(data)
    }

    // Log parser
    val logParser = LogParser()

    // Input
    val inputRedirect = PipedInputStream(INPUT_BUFFER)
    val inputStream = PrintStream(PipedOutputStream(inputRedirect))

    // Intercept IO
    System.setOut(PrintStream(LogReader {
        LogParser.parseLine(it)?.let { logParser.parseMessage(it) }
    }))
    System.setIn(inputRedirect)

    // Load Main Class
    val mainMethod: Method
    try {
        val minecraftServer = ClassLoader.getSystemClassLoader().loadClass(
                mainClass)
        mainMethod = minecraftServer.getMethod("main",
                Array<String>::class.java)
    } catch (e: ClassNotFoundException) {
        System.err.println(
                "Failed to load MinecraftServer class, please check if a " +
                        "valid Minecraft Server jar was passed as one of " +
                        "the arguments.")
        System.exit(1)
        return
    } catch (e: NoSuchMethodException) {
        System.err.println(
                "Failed to get main method, please check if a valid Minec" +
                        "raft Server jar was passed as one of the argumen" +
                        "ts.")
        System.exit(1)
        return
    } catch (e: Throwable) {
        System.err.println("Unknown error on startup: $e")
        System.exit(1)
        return
    }

    // Init connection
    if (!initWrapper(config, data, logParser, inputStream)) {
        System.err.println("Disabled wrapper features!")
    }

    // Run Server
    try {
        mainMethod.invoke(null, *arrayOf<Any>(commandLine.args))
    } catch (e: IllegalAccessException) {
        System.err.println("Failed to run Server: " + e)
        System.exit(1)
        return
    } catch (e: InvocationTargetException) {
        System.err.println("Failed to run Server: " + e)
        System.exit(1)
        return
    } catch (e: Throwable) {
        System.err.println("Unknown error on run: " + e)
        System.exit(1)
        return
    }
}

private fun initWrapper(config: FilePath,
                        data: FilePath,
                        logParser: LogParser,
                        inputStream: PrintStream): Boolean {

    // Load config
    val tagStructure = loadConfig(config.resolve("Server.json"))
    val connectionStructure = tagStructure.getStructure("Connection") ?: run {
        System.err.println("No connection in config!")
        return false
    }
    val configStructure = tagStructure.getStructure("Config") ?: run {
        System.err.println("No wrapper config in config!")
        return false
    }
    val address = RemoteAddress(connectionStructure)
    val ignoreCertificate = connectionStructure.getBoolean(
            "IgnoreCertificate") ?: false
    val uuid = tagStructure.getUUID("UUID") ?: run {
        System.err.println("No valid UUID in config!")
        return false
    }
    val privateKeyStr = tagStructure.getString("PrivateKey") ?: run {
        System.err.println("No private key in config!")
        return false
    }
    val privateKey = try {
        readPrivate(privateKeyStr)
    } catch (e: InvalidKeySpecException) {
        System.err.println("Error decoding private key: $e")
        return false
    }

    // Set up task executor
    val taskExecutor = TaskExecutor(object : Crashable {
        override fun crash(e: Throwable) {
            System.err.println("Internal crash, wrapper stopping!")
            e.printStackTrace()
        }
    }, "Wrapper")

    // Set up connection worker
    val ssl = SSLProvider.sslHandle {
        if (ignoreCertificate) {
            stdout.println("Invalid core certificate!")
            true
        } else {
            false
        }
    }
    val connection = ConnectionManager(taskExecutor)
    connection.workers(1)

    // Run wrapper
    AmbossWrapper.run(address, uuid, privateKey, configStructure, data,
            logParser, inputStream, connection, taskExecutor, ssl)

    return true
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
        setStructure("Config") {}
        setString("UUID", "")
        setString("PrivateKey", "")
    }
    write(path) { TagStructureJSON.write(tagStructure, it) }
    return tagStructure
}

object AmbossWrapper {
    val RUNTIME = Runtime.getRuntime()
    val CONNECTION_HEADER = byteArrayOf('A'.toByte(), 'm'.toByte(),
            'b'.toByte(), 'o'.toByte(), 's'.toByte(), 's'.toByte())

    fun run(address: RemoteAddress,
            uuid: UUID,
            privateKey: PrivateKey,
            config: TagStructure,
            data: FilePath,
            logParser: LogParser,
            inputStream: PrintStream,
            connection: ConnectionManager,
            taskExecutor: TaskExecutor,
            ssl: SSLHandle) {
        taskExecutor.start()
        RUNTIME.addShutdownHook(Thread {
            connection.stop()
            taskExecutor.shutdown()
        })
        val instance = WrapperInstance()
        ServerSlave(logParser,
                { synchronized(inputStream) { inputStream.println(it) } },
                instance, data)
        connect(address, uuid, privateKey, config, instance, connection,
                taskExecutor, ssl)
    }

    fun connect(address: RemoteAddress,
                uuid: UUID,
                privateKey: PrivateKey,
                config: TagStructure,
                instance: WrapperInstance,
                connection: ConnectionManager,
                taskExecutor: TaskExecutor,
                ssl: SSLHandle) {
        stdout.println("Connecting...")
        connection.addOutConnection(address, { e ->
            stdout.println("Failed to connect: $e")
            taskExecutor.addTaskOnce({
                connect(address, uuid, privateKey, config, instance, connection,
                        taskExecutor, ssl)
            }, "Reconnect", 20000)
        }) { worker, socketChannel ->
            val bundleChannel = PacketBundleChannel(address, socketChannel,
                    taskExecutor, ssl, true)
            val output = bundleChannel.outputStream
            output.put(CONNECTION_HEADER)
            output.put(1)
            bundleChannel.queueBundle()
            worker.addConnection {
                val channel = ControlPanelProtocol(worker, bundleChannel, null,
                        uuid.toString(),
                        ControlPanelProtocol.keyPairAuthentication(privateKey))
                instance.connectionInit(channel)
                channel.send("Wrapper-Init", config)
                channel.openHook { stdout.println("Connected!") }
                channel.closeHook {
                    instance.connectionClose()
                    stdout.println("Disconnected!")
                    taskExecutor.addTaskOnce({
                        connect(address, uuid, privateKey, config, instance,
                                connection, taskExecutor, ssl)
                    }, "Reconnect", 5000)
                }
                channel
            }
        }
    }
}

class WrapperInstance {
    private var send: ((String, TagStructure) -> Unit)? = null
    private val commands = ArrayList<(ControlPanelProtocol) -> Unit>()

    fun send(command: String,
             payload: TagStructure): Boolean {
        send?.let {
            it(command, payload)
            return true
        }
        return false
    }

    fun onConnect(block: ControlPanelProtocol.() -> Unit) {
        commands.add(block)
    }

    fun connectionInit(channel: ControlPanelProtocol) {
        send = { command, payload -> channel.send(command, payload) }
        commands.forEach { it(channel) }
    }

    fun connectionClose() {
        send = null
    }
}
