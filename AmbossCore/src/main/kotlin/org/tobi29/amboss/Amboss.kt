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

package org.tobi29.amboss

import mu.KLogging
import org.apache.commons.cli.*
import org.tobi29.scapes.engine.server.SSLProvider
import org.tobi29.scapes.engine.utils.Crashable
import org.tobi29.scapes.engine.utils.Version
import org.tobi29.scapes.engine.utils.exitLater
import org.tobi29.scapes.engine.utils.io.filesystem.*
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.getInt
import org.tobi29.scapes.engine.utils.io.tag.json.TagStructureJSON
import org.tobi29.scapes.engine.utils.io.tag.setInt
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

val VERSION = Version(0, 0, 0)

private val HOME_PATH = Pattern.compile("\\\$HOME")

fun main(args: Array<String>) {
    val options = Options()
    options.run {
        addOption("h", "help", false, "Print this text and exit")
        addOption("v", "version", false, "Print version and exit")
        addOption("c", "config", true, "Config directory for server")
    }
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
        helpFormatter.printHelp("ambosscore", options)
        System.exit(0)
        return
    }
    if (commandLine.hasOption('v')) {
        println("AmbossCore $VERSION")
        System.exit(0)
        return
    }
    val cmdArgs = commandLine.args
    val home: FilePath
    if (cmdArgs.size > 0) {
        home = path(HOME_PATH.matcher(cmdArgs[0]).replaceAll(
                System.getProperty("user.home"))).toAbsolutePath()
        System.setProperty("user.dir", home.toAbsolutePath().toString())
    } else {
        home = path(System.getProperty("user.dir")).toAbsolutePath()
    }
    val config: FilePath
    if (commandLine.hasOption('c')) {
        config = home.resolve(
                path(commandLine.getOptionValue('c'))).toAbsolutePath()
    } else {
        config = home
    }
    Amboss.run(config, home)
}

object Amboss : KLogging() {
    fun run(config: FilePath,
            data: FilePath) {
        val tagStructure = loadConfig(config.resolve("Core.json"))
        val keyManagerConfig = tagStructure.structure("Certificate")
        val keyManagerProvider = KeyManager()
        val ssl = SSLProvider.sslHandle(
                keyManagerProvider[config, keyManagerConfig])

        val amboss = AmbossServer(tagStructure, data, ssl, object : Crashable {
            override fun crash(e: Throwable) {
                logger.error(e) { "Stopping due to a crash" }
                val debugValues = ConcurrentHashMap<String, String>()
                try {
                    writeCrashReport(e, file(config), "ScapesServer",
                            debugValues)
                } catch (e1: IOException) {
                    logger.warn { "Failed to write crash report: $e1" }
                }
                exitLater(1)
            }
        })

        val connectionStructure = tagStructure.structure("Connection")
        amboss.run(
                InetSocketAddress(connectionStructure.getInt("Port") ?: 26555))
    }

    private fun loadConfig(path: FilePath): TagStructure {
        if (exists(path)) {
            return read(path) { TagStructureJSON.read(it) }
        }
        val tagStructure = TagStructure()
        val certificateStructure = tagStructure.structure("Certificate")
        certificateStructure.setString("Certificate", "server.crt")
        certificateStructure.setString("PrivateKey", "server.key")
        val connectionStructure = tagStructure.structure("Connection")
        connectionStructure.setInt("WorkerCount", 4)
        connectionStructure.setInt("Port", 26555)
        write(path) { TagStructureJSON.write(tagStructure, it) }
        return tagStructure
    }
}
