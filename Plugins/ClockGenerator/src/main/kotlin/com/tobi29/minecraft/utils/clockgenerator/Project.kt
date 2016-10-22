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
import java8.util.Optional
import org.tobi29.scapes.engine.utils.math.vector.Vector2i
import org.tobi29.scapes.engine.utils.math.vector.Vector3i
import java.util.*
import java.util.regex.Pattern

class Project @Throws(ProjectException::class)
constructor(properties: Properties) {
    private val replaces = ArrayList<(String) -> String>()
    private val rows = RowManager()
    val start: Vector3i
    private val size: Vector3i
    private val count: Int
    private val length: Int
    private val shelves: Int
    private val shelveHeight: Int

    init {
        start = parseVector3(properties.getProperty("rows-start", ""),
                "rows-start")
        size = parseVector3(properties.getProperty("rows-size", ""),
                "rows-size")
        length = size.x
        count = size.z
        shelveHeight = 3
        shelves = size.y / shelveHeight
    }

    private fun parseVector3(str: String,
                             error: String): Vector3i {
        val startSplit = str.split(
                " ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (startSplit.size != 3) {
            throw ProjectException("Invalid " + error)
        }
        try {
            return Vector3i(Integer.parseInt(startSplit[0]),
                    Integer.parseInt(startSplit[1]),
                    Integer.parseInt(startSplit[2]))
        } catch (e: NumberFormatException) {
            throw ProjectException("Invalid " + error)
        }

    }

    fun addRow(name: String,
               location: Optional<Vector2i>,
               source: Source): Row {
        return rows.add(name, location, length, source)
    }

    fun addReplace(pattern: String,
                   str: String) {
        val compiled = Pattern.compile(pattern)
        replaces.add(0,
                { command -> compiled.matcher(command).replaceAll(str) })
    }

    fun row(name: String,
            source: Source): Row {
        return rows.get(name, source)
    }

    fun createGenerator(): Generator {
        return Generator(count, shelves, length, shelveHeight, start, replaces)
    }

    fun generate(generator: Generator) {
        rows.generate(generator)
    }
}
