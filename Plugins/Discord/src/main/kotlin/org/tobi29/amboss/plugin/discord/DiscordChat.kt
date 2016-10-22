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
import org.tobi29.amboss.connection.WrapperConnection
import org.tobi29.amboss.plugin.event.ChatEvent
import org.tobi29.amboss.plugin.event.PlayerJoinEvent
import org.tobi29.amboss.plugin.event.PlayerLeaveEvent
import org.tobi29.amboss.util.MCColor
import org.tobi29.amboss.util.TellrawString
import org.tobi29.amboss.util.tellraw
import org.tobi29.amboss.util.tellrawMessage
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.utils.io.tag.structure
import sx.blah.discord.handle.impl.events.MessageReceivedEvent
import sx.blah.discord.handle.impl.events.MessageUpdateEvent
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.util.DiscordException
import sx.blah.discord.util.MessageBuilder
import sx.blah.discord.util.MissingPermissionsException
import sx.blah.discord.util.RateLimitException

object DiscordChat : KLogging() {
    val TELLRAW_PREFIX = TellrawString("Discord", MCColor.blue)

    fun pushChannel(plugin: DiscordPlugin,
                    wrapper: WrapperConnection,
                    channel: IChannel) {
        wrapper.events.listener<ChatEvent>(plugin, -10) { event ->
            if (event.muted) {
                return@listener
            }
            try {
                MessageBuilder(channel.client).withChannel(channel).withContent(
                        "${event.name}: ${event.message}").build()
            } catch (e: DiscordException) {
                logger.warn { "Failed to send message: $e" }
            } catch (e: RateLimitException) {
                logger.warn { "Rate limit reached: $e" }
            } catch (e: MissingPermissionsException) {
                logger.warn { "Missing permissions to send message to channel ${channel.guild.name}:${channel.name}: $e" }
            }
        }
    }

    fun messagesChannel(plugin: DiscordPlugin,
                        wrapper: WrapperConnection,
                        channel: IChannel) {
        wrapper.events.listener<PlayerJoinEvent>(plugin, -10) { event ->
            try {
                MessageBuilder(channel.client).withChannel(channel).withContent(
                        "**${event.name} joined the server**").build()
            } catch (e: DiscordException) {
                logger.warn { "Failed to send message: $e" }
            } catch (e: RateLimitException) {
                logger.warn { "Rate limit reached: $e" }
            } catch (e: MissingPermissionsException) {
                logger.warn { "Missing permissions to send message to channel ${channel.guild.name}:${channel.name}: $e" }
            }
        }
        wrapper.events.listener<PlayerLeaveEvent>(plugin, -10) { event ->
            try {
                MessageBuilder(channel.client).withChannel(channel).withContent(
                        "**${event.name} left the server**").build()
            } catch (e: DiscordException) {
                logger.warn { "Failed to send message: $e" }
            } catch (e: RateLimitException) {
                logger.warn { "Rate limit reached: $e" }
            } catch (e: MissingPermissionsException) {
                logger.warn { "Missing permissions to send message to channel ${channel.guild.name}:${channel.name}: $e" }
            }
        }
    }

    fun pullChannel(plugin: DiscordPlugin,
                    wrapper: ControlPanelProtocol,
                    channel: IChannel) {
        channel.client.dispatcher.attachListener<MessageReceivedEvent>(
                wrapper) { event ->
            val message = event.message
            if (message.channel == channel) {
                wrapper.send("Command", structure {
                    setString("Command", tellraw("@a",
                            tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                    "${message.author.name}: ${message.content}"
                            ))))
                })
            }
        }
        channel.client.dispatcher.attachListener<MessageUpdateEvent>(
                wrapper) { event ->
            val message = event.newMessage
            if (message.channel == channel) {
                wrapper.send("Command", structure {
                    setString("Command", tellraw("@a",
                            tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                    "${message.author.name}: ${message.content}"
                            ))))
                })
            }
        }
    }
}