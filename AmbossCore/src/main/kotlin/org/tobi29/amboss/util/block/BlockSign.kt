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

import org.tobi29.amboss.util.block.CommandUtil
import org.tobi29.scapes.engine.utils.math.Face

class BlockSign(private val text1: String, private val text2: String, private val text3: String, private val text4: String,
                private val face: Face) : Block {

    constructor(face: Face) : this("{\"text\":\"\"}", face) {
    }

    constructor(text1: String, face: Face) : this(text1, "{\"text\":\"\"}",
            face) {
    }

    constructor(text1: String, text2: String, face: Face) : this(text1, text2,
            "{\"text\":\"\"}", face) {
    }

    constructor(text1: String, text2: String, text3: String, face: Face) : this(
            text1, text2, text3, "{\"text\":\"\"}", face) {
    }

    override fun eval(x: Int,
                      y: Int,
                      z: Int): Array<String> {
        val type: String
        val data: Int
        val coordsBase: String
        when (face) {
            Face.NORTH -> {
                type = "minecraft:wall_sign"
                data = 3
                coordsBase = "$x $y ${z - 1}"
            }
            Face.EAST -> {
                type = "minecraft:wall_sign"
                data = 4
                coordsBase = "${x + 1} $y $z"
            }
            Face.SOUTH -> {
                type = "minecraft:wall_sign"
                data = 2
                coordsBase = "$x $y ${z + 1}"
            }
            Face.WEST -> {
                type = "minecraft:wall_sign"
                data = 5
                coordsBase = "${x - 1} $y $z"
            }
            else -> {
                type = "minecraft:standing_sign"
                data = 4
                coordsBase = "$x ${y - 1} $z"
            }
        }
        return arrayOf(
                "/setblock $x $y $z $type $data replace {Text1:\"${CommandUtil.escape(
                        text1)}\",Text2:\"${CommandUtil.escape(
                        text2)}\",Text3:\"${CommandUtil.escape(
                        text3)}\",Text4:\"${CommandUtil.escape(text4)}\"}",
                "/setblock $coordsBase minecraft:fence 0 keep")
    }
}
