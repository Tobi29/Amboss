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
package com.tobi29.minecraft.utils.clockgenerator

import com.tobi29.minecraft.utils.clockgenerator.parser.ParserException
import org.tobi29.scapes.engine.utils.math.vector.Vector2i
import org.tobi29.scapes.engine.utils.readOnly
import java.util.*
import java.util.regex.Pattern

class Row(val name: String, val location: Vector2i?, private val batchSize: Int, val source: Source) : Section {
    private val commandsMut = ArrayList<RowBatch>()
    val commands = commandsMut.readOnly()
    private var currentBatch: RowBatch? = null
    private var previous = Optional.empty<Command>()

    init {
        val batch = RowBatch(batchSize)
        currentBatch = batch
        commandsMut.add(batch)
        addCommand(CommandActivator(name, source))
        addCommand(CommandImpulse(source))
    }

    val size: Int
        get() = commandsMut.size

    override fun add(line: String,
                     source: Source) {
        val initial = line[0]
        when (initial) {
            ':' -> {
                val split = SPLIT.split(line.substring(1))
                if (split.size != 2) {
                    throw ParserException(source,
                            "Row-trigger missing \" -> \"")
                }
                addCommand(CommandRowTrigger(split[0], split[1], source))
            }
            '&' -> if (previous.isPresent) {
                previous.get().addProcess(line.substring(1))
            } else {
                throw ParserException(source, "No command available in row")
            }
            else -> if (line.startsWith("delay ")) {
                val length = line.substring(6)
                try {
                    addCommandGroup(source, CommandDelayInit("",
                            Integer.parseInt(length), source),
                            CommandDelayWait(source), CommandDelayReset(source),
                            CommandDelayContinue("", source))
                } catch (e: NumberFormatException) {
                    throw ParserException(source, "Invalid number: " + length)
                }
            } else {
                addCommand(Command(line, source))
            }
        }
    }

    override fun end() {
    }

    private fun newBatch() {
        if (currentBatch!!.remaining() != 0) {
            throw IllegalStateException("Unfinished batch left behind")
        }
        val batch = RowBatch(batchSize)
        currentBatch = batch
        commandsMut.add(batch)
    }

    private fun addCommand(command: Command) {
        if (currentBatch!!.remaining() == 0) {
            newBatch()
        }
        currentBatch!!.plusAssign(command)
        previous = Optional.of(command)
    }

    @Throws(ParserException::class)
    private fun addCommandGroup(source: Source,
                                vararg command: Command) {
        if (command.size == 0) {
            return
        }
        if (command.size >= batchSize) {
            throw ParserException(source,
                    "Row size is too short for grouped commands (Row size: " +
                            batchSize + ", Group size: " + command.size +
                            ", Row size has to be one greater than group size")
        }
        if (currentBatch!!.remaining() <= command.size) {
            while (currentBatch!!.remaining() != 0) {
                currentBatch!!.plusAssign(Command("", source))
            }
            newBatch()
        }
        currentBatch!!.plusAssign(*command)
        previous = Optional.of(command[command.size - 1])
    }

    companion object {
        private val SPLIT = Pattern.compile(" -> ")
    }
}
