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

import com.tobi29.minecraft.utils.clockgenerator.generator.Generator
import com.tobi29.minecraft.utils.clockgenerator.generator.GeneratorException
import com.tobi29.minecraft.utils.clockgenerator.parser.ParserException
import java8.util.Optional
import org.tobi29.scapes.engine.utils.math.vector.Vector2i
import org.tobi29.scapes.engine.utils.stream
import java.util.concurrent.ConcurrentHashMap

class RowManager {
    private val rows = ConcurrentHashMap<String, Row>()

    fun add(name: String,
            location: Optional<Vector2i>,
            batchSize: Int,
            source: Source): Row {
        if (rows.containsKey(name)) {
            throw ParserException(source, "Duplicate row: $name")
        }
        val row = Row(name, location, batchSize, source)
        rows.put(name, row)
        return row
    }

    fun get(name: String,
            source: Source): Row {
        val row = rows[name] ?: throw GeneratorException(source,
                "Unknown row: $name")
        return row
    }

    fun generate(generator: Generator) {
        val iterator = rows.values.stream().sorted({ row1, row2 ->
            val location1 = row1.location
            val location2 = row2.location
            if (location1.isPresent() && !location2.isPresent()) {
                return@sorted -1
            }
            if (location2.isPresent() && !location1.isPresent()) {
                return@sorted 1
            }
            if (location1.isPresent()) {
                return@sorted 0
            }
            val length1 = row1.size
            val length2 = row2.size
            if (length1 > length2) {
                return@sorted -1
            }
            if (length2 > length1) {
                return@sorted 1
            }
            0
        }).iterator()
        while (iterator.hasNext()) {
            generator.addRow(iterator.next())
        }
    }
}
