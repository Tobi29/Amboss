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
import org.apache.commons.cli.*
import org.tobi29.scapes.engine.server.*
import org.tobi29.scapes.engine.utils.Crashable
import org.tobi29.scapes.engine.utils.exitLater
import org.tobi29.scapes.engine.utils.task.TaskExecutor
import org.tobi29.scapes.engine.utils.task.start
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern

fun main(args: Array<String>) {
    val options = Options()
    options.addOption("h", "help", false, "Print this text and exit")
    options.addOption("v", "version", false, "Print version and exit")
    options.addOption("a", "address", true, "Remote address")
    options.addOption("p", "port", true, "Remote port")
    options.addOption("u", "user", true, "Username for login")
    options.addOption("i", "ignore", false,
            "Ignore server certificate (insecure!)")
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
    val hostname = commandLine.getOptionValue('a', "localhost")
    val port = commandLine.getOptionValue('p', "26555")
    val address = try {
        RemoteAddress(hostname, port.toInt())
    } catch (e: NumberFormatException) {
        AmbossShell.logger.error { "Invalid port: $port" }
        System.exit(1)
        return
    }
    val user = commandLine.getOptionValue('u') ?: run {
        AmbossShell.logger.error { "No username given!" }
        System.exit(1)
        return
    }
    val ignoreCertificate = commandLine.hasOption('i')

    // Set up input reader
    val console = System.console()
    val lineReader = if (console == null) {
        { readLine() }
    } else {
        { console.readLine() }
    }

    // Read password
    println("Password:")
    val password = if (console == null) {
        readLine() ?: ""
    } else {
        String(console.readPassword())
    }

    // Set up task executor
    val taskExecutor = TaskExecutor(object : Crashable {
        override fun crash(e: Throwable) {
            AmbossShell.logger.error(e) { "A fatal exception occurred" }
            exitLater(1)
        }
    }, "Shell")

    // Start background reader
    val input = ConcurrentLinkedQueue<String>()
    val readerThread = Thread {
        while (true) {
            lineReader()?.let { input.add(it) }
        }
    }
    readerThread.name = "Input-Reader"
    readerThread.isDaemon = true
    readerThread.start()

    // Set up connection worker
    val ssl = SSLProvider.sslHandle {
        if (ignoreCertificate) {
            AmbossShell.logger.warn("Invalid core certificate!")
            true
        } else {
            false
        }
    }
    val connection = ConnectionManager(taskExecutor)
    connection.workers(1)

    AmbossShell.run(address, user, password, input, connection, taskExecutor,
            ssl)
}

object AmbossShell : KLogging() {
    val RUNTIME = Runtime.getRuntime()
    val CONNECTION_HEADER = byteArrayOf('A'.toByte(), 'm'.toByte(),
            'b'.toByte(), 'o'.toByte(), 's'.toByte(), 's'.toByte())
    val COMMAND_SPLIT = Pattern.compile(" ")

    fun run(address: RemoteAddress,
            user: String,
            password: String,
            input: Queue<String>,
            connection: ConnectionManager,
            taskExecutor: TaskExecutor,
            ssl: SSLHandle) {
        taskExecutor.start()
        RUNTIME.addShutdownHook(Thread {
            connection.stop()
            taskExecutor.shutdown()
        })
        connect(address, user, password, input, connection, taskExecutor, ssl)
    }

    fun connect(address: RemoteAddress,
                user: String,
                password: String,
                input: Queue<String>,
                connection: ConnectionManager,
                taskExecutor: TaskExecutor,
                ssl: SSLHandle) {
        logger.info { "Connecting..." }
        connection.addOutConnection(address, { e ->
            logger.error { "Failed to connect: $e" }
            exitLater(1)
        }) { worker, socketChannel ->
            val bundleChannel = PacketBundleChannel(address, socketChannel,
                    taskExecutor, ssl, true)
            val output = bundleChannel.outputStream
            output.put(AmbossShell.CONNECTION_HEADER)
            output.put(2)
            bundleChannel.queueBundle()
            worker.addConnection {
                val channel = ControlPanelProtocol(worker, bundleChannel, null,
                        user,
                        ControlPanelProtocol.passwordAuthentication(password))
                val commands = ShellCommands(channel)
                channel.openHook { logger.info { "Connected!" } }
                taskExecutor.addTask({
                    if (channel.isClosed) {
                        return@addTask -1
                    }
                    while (input.isNotEmpty()) {
                        commands.execute(*COMMAND_SPLIT.split(input.poll()))
                    }
                    100
                }, "Input-Reader")
                channel.closeHook {
                    logger.info { "Disconnected!" }
                    exitLater(0)
                }
                channel
            }
        }
    }
}
