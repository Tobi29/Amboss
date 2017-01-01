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
import org.tobi29.amboss.connection.KickstarterConnection
import org.tobi29.amboss.connection.ServerConnection
import org.tobi29.amboss.connection.WrapperConnection
import org.tobi29.amboss.plugin.Plugins
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.server.SSLHandle
import org.tobi29.scapes.engine.utils.Crashable
import org.tobi29.scapes.engine.utils.EventDispatcher
import org.tobi29.scapes.engine.utils.io.filesystem.FilePath
import org.tobi29.scapes.engine.utils.io.filesystem.exists
import org.tobi29.scapes.engine.utils.io.filesystem.read
import org.tobi29.scapes.engine.utils.io.filesystem.write
import org.tobi29.scapes.engine.utils.io.tag.*
import org.tobi29.scapes.engine.utils.io.tag.json.TagStructureJSON
import org.tobi29.scapes.engine.utils.readPublic
import org.tobi29.scapes.engine.utils.task.TaskExecutor
import org.tobi29.scapes.engine.utils.task.start
import org.tobi29.scapes.engine.utils.writePublic
import java.io.IOException
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class AmbossServer(configStructure: TagStructure,
                   private val data: FilePath,
                   ssl: SSLHandle,
                   crashHandler: Crashable) {
    val connection: ServerConnection
    val taskExecutor: TaskExecutor
    val plugins: Plugins
    val events = EventDispatcher()
    private val servers = ConcurrentHashMap<UUID, ControlPanelProtocol>()
    private val serverKeys = ConcurrentHashMap<UUID, Pair<String, PublicKey>>()

    init {
        taskExecutor = TaskExecutor(crashHandler, "Server")

        val connectionStructure = configStructure.structure("Connection")
        connection = ServerConnection(this, taskExecutor, CONNECTION_HEADER,
                ssl, configStructure.structure("Admins"))

        loadPersistent(data.resolve("Data.json"))

        plugins = Plugins(this, configStructure)

        connection.workers(connectionStructure.getInt("WorkerCount") ?: 1)
    }

    fun run(address: InetSocketAddress) {
        taskExecutor.start {
            connection.stop()
            plugins.dispose()
        }
        RUNTIME.addShutdownHook(Thread {
            connection.stop()
            taskExecutor.shutdown()
        })
        connection.start(address)
    }

    fun serversAdd(server: ControlPanelProtocol) {
        val uuid = try {
            UUID.fromString(server.id)
        } catch (e: IllegalArgumentException) {
            throw IOException(e)
        }
        servers.put(uuid, server)?.let(ControlPanelProtocol::requestClose)
        server.closeHook {
            servers.remove(uuid, server)
        }
    }

    fun serversGet(uuid: UUID): WrapperConnection? {
        val server = servers[uuid]
        if (server is WrapperConnection) {
            return server
        }
        return null
    }

    fun kickstartersGet(uuid: UUID): KickstarterConnection? {
        val server = servers[uuid]
        if (server is KickstarterConnection) {
            return server
        }
        return null
    }

    fun serversList(): Sequence<Pair<UUID, WrapperConnection>> {
        return servers.entries.asSequence().filter {
            it.value is WrapperConnection
        }.map {
            Pair(it.key, it.value as WrapperConnection)
        }
    }

    fun kickstartersList(): Sequence<Pair<UUID, KickstarterConnection>> {
        return servers.entries.asSequence().filter {
            it.value is KickstarterConnection
        }.map {
            Pair(it.key, it.value as KickstarterConnection)
        }
    }

    fun serverKeysAdd(name: String,
                      consumer: (UUID, KeyPair) -> Unit) {
        taskExecutor.runTask({
            val uuid = UUID.randomUUID()
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(4096)
            val keyPair = keyPairGenerator.genKeyPair()
            serverKeys.put(uuid, Pair(name, keyPair.public))
            writePersistent(data.resolve("Data.json"))
            consumer(uuid, keyPair)
        }, "Generate-Key")
    }

    fun serverKeysRemove(uuid: UUID): Boolean {
        if (serverKeys.remove(uuid) != null) {
            writePersistent(data.resolve("Data.json"))
            return true
        }
        return false
    }

    fun serverKeysList(): Sequence<Triple<UUID, String, PublicKey>> {
        return serverKeys.entries.asSequence().map {
            Triple(it.key, it.value.first, it.value.second)
        }
    }

    fun serverKeyGet(uuid: UUID): Pair<String, PublicKey>? {
        return serverKeys[uuid]
    }

    private fun loadPersistent(path: FilePath) {
        if (!exists(path)) {
            return
        }
        val tagStructure = read(path) { TagStructureJSON.read(it) }
        tagStructure.getListStructure("Servers") { serverStructure ->
            try {
                serverStructure.getUUID("UUID")?.let { uuid ->
                    serverStructure.getString("Name")?.let { name ->
                        serverStructure.getString("PublicKey")?.let {
                            val publicKey = readPublic(it)
                            serverKeys.put(uuid, Pair(name, publicKey))
                        }
                    }
                }
            } catch (e: InvalidKeySpecException) {
                logger.warn { "Failed to read server key: $e" }
            }
        }
    }

    @Synchronized private fun writePersistent(path: FilePath) {
        val tagStructure = TagStructure()
        val serverList = serverKeys.entries.mapNotNull {
            try {
                val serverStructure = TagStructure()
                serverStructure.setUUID("UUID", it.key)
                serverStructure.setString("Name", it.value.first)
                serverStructure.setString("PublicKey",
                        writePublic(it.value.second))
                serverStructure
            } catch (e: InvalidKeySpecException) {
                logger.warn { "Failed to write server key: $e" }
                null
            }
        }
        tagStructure.setList("Servers", serverList)
        write(path) { TagStructureJSON.write(tagStructure, it) }
    }

    companion object : KLogging() {
        private val RUNTIME = Runtime.getRuntime()
        val CONNECTION_HEADER = byteArrayOf('A'.toByte(), 'm'.toByte(),
                'b'.toByte(), 'o'.toByte(), 's'.toByte(), 's'.toByte())
    }
}
