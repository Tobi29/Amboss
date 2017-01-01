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

package org.tobi29.amboss.plugin.discord

import mu.KLogging
import org.tobi29.amboss.AmbossServer
import org.tobi29.amboss.connection.WrapperConnection
import org.tobi29.amboss.plugin.Plugin
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.getListString
import sx.blah.discord.api.ClientBuilder
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.handle.impl.events.ReadyEvent

class DiscordPlugin(amboss: AmbossServer,
                    token: String) : Plugin(amboss) {
    val discord: IDiscordClient

    init {
        val clientBuilder = ClientBuilder()
        clientBuilder.withToken(token)
        discord = clientBuilder.login()
    }

    override fun initServer(wrapper: WrapperConnection,
                            configStructure: TagStructure) {
        configStructure.getStructure("Discord")?.let { discordStructure ->
            discordStructure.getStructure("Chat")?.let { chatStructure ->
                chatStructure.getListString("Push") { channelID ->
                    access { discord ->
                        val channel = discord.getChannelByID(channelID)
                        if (channel == null) {
                            logger.warn { "Invalid channel: $channelID" }
                        } else {
                            DiscordChat.pushChannel(this, wrapper, channel)
                        }
                    }
                }
                chatStructure.getListString("Messages") { channelID ->
                    access { discord ->
                        val channel = discord.getChannelByID(channelID)
                        if (channel == null) {
                            logger.warn { "Invalid channel: $channelID" }
                        } else {
                            DiscordChat.messagesChannel(this, wrapper, channel)
                        }
                    }
                }
                chatStructure.getListString("Pull") { channelID ->
                    access { discord ->
                        val channel = discord.getChannelByID(channelID)
                        if (channel == null) {
                            logger.warn { "Invalid channel: $channelID" }
                        } else {
                            DiscordChat.pullChannel(this, wrapper, channel)
                        }
                    }
                }
            }
        }
    }

    override fun dispose() {
        discord.logout()
    }

    private fun access(block: (IDiscordClient) -> Unit) {
        if (discord.isReady) {
            block(discord)
        } else {
            discord.dispatcher.registerTemporaryListener<ReadyEvent> {
                block(discord)
            }
        }
    }

    companion object : KLogging()
}
