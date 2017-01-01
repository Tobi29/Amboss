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
package com.tobi29.minecraft.utils.clockgenerator.generator

import com.tobi29.minecraft.utils.clockgenerator.Row
import com.tobi29.minecraft.utils.clockgenerator.RowBatch
import com.tobi29.minecraft.utils.clockgenerator.Source
import com.tobi29.minecraft.utils.clockgenerator.parser.ParserException
import org.tobi29.amboss.util.block.Block
import org.tobi29.scapes.engine.utils.math.Face
import org.tobi29.scapes.engine.utils.math.vector.Vector3i
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class Generator(rowCount: Int,
                shelveCount: Int,
                private val rowLength: Int,
                private val shelveHeight: Int,
                private val start: Vector3i,
                private val replaces: List<(String) -> String>) {
    private val used: Array<BooleanArray>
    private val rows: Array<Array<Row?>>
    private val rowLocations = ConcurrentHashMap<String, Vector3i>()

    init {
        used = Array(shelveCount) { BooleanArray(rowCount) }
        rows = Array(shelveCount) { arrayOfNulls<Row>(rowCount) }
    }

    fun addRow(row: Row) {
        var xx = -1
        var yy = -1
        val location = row.location
        val required = row.size
        if (location != null) {
            xx = location.x
            yy = location.y
        } else {
            var y = 0
            while (y < used.size && xx < 0) {
                var x = 0
                while (x < used[0].size && xx < 0) {
                    if (x + required < used[0].size) {
                        var flag = true
                        for (i in 0..required - 1) {
                            if (used[y][x + i]) {
                                flag = false
                                break
                            }
                        }
                        if (flag) {
                            xx = x
                            yy = y
                        }
                    }
                    x++
                }
                y++
            }
        }
        if (xx < 0) {
            throw GeneratorException(row.source,
                    "Unable to fit row: ${row.name}")
        }
        for (i in 0..required - 1) {
            if (used[yy][xx + i]) {
                throw GeneratorException(row.source,
                        "Overlapping row: ${row.name}")
            }
            used[yy][xx + i] = true
        }
        rows[yy][xx] = row
        val xxx = start.x
        val yyy = start.y + yy * shelveHeight
        val zzz = start.z + xx
        rowLocations.put(row.name, Vector3i(xxx, yyy, zzz))
    }

    fun getRow(name: String,
               source: Source): Vector3i {
        val row = rowLocations[name] ?: throw ParserException(source,
                "Unknown row: $name")
        return row
    }

    fun generate(): Array<Array<Array<Block?>>> {
        val blocks = Array(rowLength) {
            Array((used.size - 1) * shelveHeight + 4) {
                arrayOfNulls<Block>(used[0].size)
            }
        }
        for (y in used.indices) {
            val yy = y * shelveHeight
            for (z in 0..used[0].size - 1) {
                val row = rows[y][z]
                if (row != null) {
                    var zz = z
                    val iterator = row.commands.iterator()
                    var flipped = false
                    while (iterator.hasNext()) {
                        val batch = iterator.next()
                        generateRowBatch(blocks, yy, zz, !iterator.hasNext(),
                                flipped, batch)
                        flipped = !flipped
                        zz++
                    }
                }
            }
        }
        return blocks
    }

    private fun generateRowBatch(blocks: Array<Array<Array<Block?>>>,
                                 y: Int,
                                 z: Int,
                                 last: Boolean,
                                 flipped: Boolean,
                                 batch: RowBatch) {
        var x = 0
        batch.commands.forEach { command ->
            if (command == null) {
                return@forEach
            }
            val xx: Int
            if (flipped) {
                xx = rowLength - x - 1
            } else {
                xx = x
            }
            val face: Face
            if (x == rowLength - 1 && !last) {
                face = Face.SOUTH
            } else {
                face = if (flipped) Face.WEST else Face.EAST
            }
            command.place(this, blocks, xx, y, z, face)
            x++
        }
    }

    fun preprocess(str: String): String {
        var str = str
        for (replace in replaces) {
            str = replace(str)
        }
        val matcher = REPLACE_RANDOM_UUID.matcher(str)
        val output = StringBuffer(str.length)
        while (matcher.find()) {
            val uuid = UUID.randomUUID()
            val most = uuid.mostSignificantBits
            val least = uuid.leastSignificantBits
            matcher.appendReplacement(output,
                    "UUIDMost:${most}L,UUIDLeast:${least}L")
        }
        matcher.appendTail(output)
        return output.toString()
    }

    companion object {
        private val REPLACE_RANDOM_UUID = Pattern.compile("\\\$RAND_UUID\\$")
    }
}
