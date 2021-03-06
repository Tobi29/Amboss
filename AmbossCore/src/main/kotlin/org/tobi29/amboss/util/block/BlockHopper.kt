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

class BlockHopper(private val face: Face) : Block {

    override fun eval(x: Int,
                      y: Int,
                      z: Int): Array<String> {
        val data: Int
        when (face) {
            Face.NORTH -> data = 2
            Face.EAST -> data = 5
            Face.SOUTH -> data = 3
            Face.WEST -> data = 4
            else -> data = 0
        }
        return arrayOf("/setblock $x $y $z minecraft:hopper $data")
    }
}
