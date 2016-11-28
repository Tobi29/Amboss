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

package org.tobi29.amboss.plugin.autorestart

import mu.KLogging
import org.threeten.bp.Clock
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalTime
import org.threeten.bp.format.DateTimeParseException
import org.tobi29.amboss.AmbossServer
import org.tobi29.amboss.connection.WrapperConnection
import org.tobi29.amboss.connection.stop
import org.tobi29.amboss.plugin.Plugin
import org.tobi29.amboss.plugin.event.ChatEvent
import org.tobi29.amboss.util.MCColor
import org.tobi29.amboss.util.TellrawString
import org.tobi29.amboss.util.tellraw
import org.tobi29.amboss.util.tellrawMessage
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.structure
import org.tobi29.scapes.engine.utils.math.min
import java.util.regex.Pattern

class AutoRestartPlugin(amboss: AmbossServer) : Plugin(amboss) {
    val TELLRAW_PREFIX = TellrawString("Auto Restart", MCColor.red)

    override fun initServer(wrapper: WrapperConnection,
                            configStructure: TagStructure) {
        configStructure.getStructure(
                "AutoRestart")?.let { autoRestartStructure ->
            autoRestartStructure.getStructure(
                    "Daily")?.let { dailyStructure ->
                dailyStructure.getString(
                        "From")?.let { fromStr ->
                    dailyStructure.getString(
                            "To")?.let { toStr ->
                        try {
                            val from = LocalTime.parse(fromStr)
                            val to = LocalTime.parse(toStr)
                            daily(wrapper, from, to)
                        } catch (e: DateTimeParseException) {
                        }
                    }
                }
            }
        }
        val permissions = configStructure.structure("Permissions")
        wrapper.events.listener<ChatEvent>(this, 10) { event ->
            if (event.muted) {
                return@listener
            }
            if (!(permissions.getList(event.name)?.contains(
                    "AutoRestart") ?: false)) {
                return@listener
            }
            if (event.message.startsWith("!restart")) {
                event.muted = true
                try {
                    val split = COMMAND_SPLIT.split(event.message)
                    if (split.size != 2) {
                        throw IllegalArgumentException(
                                "Invalid amount of arguments")
                    }
                    val time = split[1].toInt()
                    wrapper.stopGracefully(time)
                } catch(e: IllegalArgumentException) {
                    wrapper.send("Command", structure {
                        setString("Command", tellraw("@a",
                                tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                        "Usage: !restart <time>"))))
                    })
                }
            }
        }
    }

    private fun daily(wrapper: WrapperConnection,
                      from: LocalTime,
                      to: LocalTime) {
        val clock = Clock.systemUTC()
        val nextFrom = nextOn(clock, from)
        val nextTo = nextOn(nextFrom, to)
        amboss.taskExecutor.addTask({
            if (wrapper.isClosed) {
                return@addTask -1
            }
            val current = LocalDateTime.now(clock)
            if (!current.isBefore(nextFrom)) {
                if (wrapper.players.isEmpty()) {
                    wrapper.stop()
                    return@addTask -1
                } else if (!current.isBefore(nextTo)) {
                    wrapper.stopGracefully(5 * 60)
                    return@addTask -1
                }
            }
            10000
        }, "AutoRestart-Dispatcher")
    }

    private fun WrapperConnection.stopGracefully(time: Int) {
        amboss.taskExecutor.addTask({
            if (players.isEmpty()) {
                stop()
                return@addTask -1
            }
            1000
        }, "AutoRestart-QuickRestart")
        var remaining = time
        amboss.taskExecutor.addTask({
            if (isClosed) {
                return@addTask -1
            }
            if (remaining == 0) {
                stop()
                return@addTask -1
            }
            val delay = stopDelayAt(remaining)
            send("Command", structure {
                setString("Command", tellraw("@a",
                        tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                "Restarting in ${callout(remaining)}..."))))
            })
            remaining -= delay
            1000L * delay
        }, "AutoRestart-Restart")
    }

    private fun stopDelayAt(time: Int) =
            with(time, 60) {
                with(time, 30) {
                    with(time, 10) {
                        1
                    }
                }
            }

    private inline fun with(time: Int,
                            interval: Int,
                            or: () -> Int): Int {
        if (time > interval) {
            return min(time - interval, interval)
        }
        return or()
    }

    private fun callout(time: Int): String {
        if (time == 1) {
            return "1 second"
        }
        if (time <= 60) {
            return "$time seconds"
        }
        val minutes = time / 60
        val seconds = time % 60
        val minutesCall = if (minutes == 1) {
            "1 minute"
        } else {
            "$minutes minutes"
        }
        if (seconds == 0) {
            return minutesCall
        }
        if (seconds == 1) {
            return "$minutesCall and 1 second"
        }
        return "$minutesCall and $seconds seconds"
    }

    private fun nextOn(clock: Clock,
                       time: LocalTime): LocalDateTime {
        return nextOn(LocalDateTime.now(clock), time)
    }

    private fun nextOn(instant: LocalDateTime,
                       time: LocalTime): LocalDateTime {
        val current = LocalDateTime.from(instant)
        val date = LocalDate.from(current)
        var next = date.atTime(time)
        while (next.isBefore(current)) {
            next = next.plusDays(1)
        }
        return next
    }

    companion object : KLogging() {
        private val COMMAND_SPLIT = Pattern.compile(" ")
    }
}
