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

package org.tobi29.amboss.plugin

import mu.KLogging
import org.tobi29.amboss.AmbossServer
import org.tobi29.amboss.connection.WrapperConnection
import org.tobi29.amboss.plugin.spi.PluginProvider
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import java.util.*

class Plugins(val amboss: AmbossServer, configStructure: TagStructure) {
    private val plugins = ArrayList<Plugin>()

    init {
        for (provider in ServiceLoader.load(PluginProvider::class.java)) {
            try {
                val name = provider.name
                val plugin = provider.create(amboss,
                        configStructure.structure(name))
                if (plugin != null) {
                    logger.info { "Loaded plugin: $name" }
                    plugins.add(plugin)
                }
            } catch (e: ServiceConfigurationError) {
                logger.warn { "Unable to load plugin: $e" }
            }
        }
    }

    fun initServer(wrapper: WrapperConnection,
                   configStructure: TagStructure) {
        plugins.forEach { it.initServer(wrapper, configStructure) }
    }

    fun initShell(channel: ControlPanelProtocol) {
        plugins.forEach { it.initShell(channel) }
    }

    fun dispose() {
        plugins.forEach(Plugin::dispose)
    }

    companion object : KLogging()
}
