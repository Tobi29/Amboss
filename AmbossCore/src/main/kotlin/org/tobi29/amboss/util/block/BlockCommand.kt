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

package org.tobi29.amboss.util.block

import org.tobi29.scapes.engine.utils.math.Face
import org.tobi29.scapes.engine.utils.toArray
import java.util.regex.Pattern

class BlockCommand(private val command: String, private val process: Array<out String>, private val face: Face,
                   private val type: CommandType, private val auto: Boolean, private val conditional: Boolean) : Block {

    constructor(pair: Pair<String, Array<String>>) : this(pair.first,
            *pair.second) {
    }

    constructor(command: String, vararg process: String) : this(command,
            process, Face.NONE, CommandType.NORMAL, false, false) {
    }

    constructor(command: String, face: Face, type: CommandType,
                auto: Boolean, conditional: Boolean) : this(command,
            EMPTY_STRING, face, type, auto, conditional) {
    }

    override fun eval(x: Int,
                      y: Int,
                      z: Int): Array<String> {
        val type: String
        when (this.type) {
            CommandType.NORMAL -> type = "minecraft:command_block"
            CommandType.CHAIN -> type = "minecraft:chain_command_block"
            CommandType.REPEATING -> type = "minecraft:repeating_command_block"
            else -> throw IllegalArgumentException(
                    "Unknown command block type: " + this.type)
        }
        var data: Int
        when (face) {
            Face.UP -> data = 1
            Face.NORTH -> data = 2
            Face.EAST -> data = 5
            Face.SOUTH -> data = 3
            Face.WEST -> data = 4
            else -> data = 0
        }
        if (conditional) {
            data += 8
        }
        return arrayOf(
                "/setblock $x $y $z $type $data replace {Command:\"${CommandUtil.escape(
                        command)}\",auto:${if (auto) 1 else 0},TrackOutput:0}",
                *process.asSequence().map {
                    REPLACE_Z.matcher(REPLACE_Y.matcher(
                            REPLACE_X.matcher(it).replaceAll(
                                    x.toString())).replaceAll(
                            y.toString())).replaceAll(z.toString())
                }.toArray())
    }

    companion object {
        val EMPTY_STRING = arrayOf<String>()
        private val REPLACE_X = Pattern.compile("%x")
        private val REPLACE_Y = Pattern.compile("%y")
        private val REPLACE_Z = Pattern.compile("%z")
    }
}
