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

import org.tobi29.amboss.util.block.Block
import org.tobi29.scapes.engine.utils.math.vector.Vector3i

inline fun writeBlocks(start: Vector3i,
                       blocks: Array<Array<Array<Block?>>>,
                       output: (String) -> Unit) {
    val sx = start.x
    val sy = start.y
    val sz = start.z
    for (x in blocks.indices) {
        output("/fill ${sx + x} $sy $sz ${sx + x} ${sy + blocks[0].size} ${sz + blocks[0][0].size} minecraft:air")
    }
    for (x in blocks.indices) {
        for (y in 0..blocks[0].size - 1) {
            for (z in 0..blocks[0][0].size - 1) {
                val block = blocks[x][y][z]
                if (block != null) {
                    val command = block.eval(sx + x, sy + y, sz + z)
                    for (str in command) {
                        output(str)
                    }
                }
            }
        }
    }
}
