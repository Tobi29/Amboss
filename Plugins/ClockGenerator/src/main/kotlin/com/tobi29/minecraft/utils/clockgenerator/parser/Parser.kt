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
package com.tobi29.minecraft.utils.clockgenerator.parser

import com.tobi29.minecraft.utils.clockgenerator.Project
import com.tobi29.minecraft.utils.clockgenerator.Section
import com.tobi29.minecraft.utils.clockgenerator.Source
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.join
import org.tobi29.scapes.engine.utils.math.vector.Vector2i
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.regex.Pattern

class Parser(properties: Properties) {
    private val project: Project
    private var section: Section? = null

    init {
        project = Project(properties)
    }

    private fun parseStream(tagStructure: TagStructure,
                            path: Array<String>,
                            file: String): String {
        val currentFile = join(*path, file, delimiter = "/")
        val data = getEntry(tagStructure, path, file) ?: throw IOException(
                "No $currentFile!")
        val reader = BufferedReader(
                InputStreamReader(ByteArrayInputStream(data)))
        var line: String? = reader.readLine()
        val lineBuilder = StringBuilder(4096)
        var i = 1
        while (line != null) {
            line = line.trim { it <= ' ' }
            if (!line.isEmpty()) {
                val initial = line[0]
                when (initial) {
                    '#' -> {
                    }
                    '§' -> {
                        val includeFile = line.substring(1)
                        val include = getPath(path, includeFile)
                        lineBuilder.append(
                                parseStream(tagStructure, include.first,
                                        include.second))
                    }
                    else -> {
                        var semicolon = line.indexOf(';')
                        var lastSemicolon = 0
                        while (semicolon >= 0) {
                            if (semicolon >= 0) {
                                lineBuilder.append(line.substring(lastSemicolon,
                                        semicolon))
                                if (lineBuilder.length > 0) {
                                    parseLine(lineBuilder.toString(),
                                            Source(currentFile, i))
                                    lineBuilder.setLength(0)
                                }
                                lastSemicolon = semicolon + 1
                            }
                            semicolon = line.indexOf(';', semicolon + 1)
                        }
                        if (lastSemicolon < line.length) {
                            lineBuilder.append(
                                    line.substring(lastSemicolon, line.length))
                        }
                    }
                }
            }
            i++
            line = reader.readLine()
        }
        return lineBuilder.toString()
    }

    private fun getPath(path: Array<String>,
                        file: String): Pair<Array<String>, String> {
        val split = PATH_SPLIT.split(file)
        if (split.size == 1) {
            return Pair(path, split.last())
        }
        return Pair(arrayOf(*path, *Array(split.size - 1) { split[it] }),
                split.last())
    }

    private fun getEntry(tagStructure: TagStructure,
                         path: Array<String>,
                         file: String): ByteArray? {
        var childStructure = tagStructure
        for (element in path) {
            childStructure = childStructure.getStructure(element) ?: return null
        }
        return childStructure.getByteArray(file)
    }

    private fun parseLine(line: String,
                          source: Source) {
        val initial = line[0]
        when (initial) {
            '-' -> {
                section?.let { it.end() }
                val split = line.substring(1).split(" ".toRegex(),
                        4).toTypedArray()
                val location = if (split.size == 3) {
                    try {
                        Vector2i(Integer.parseInt(split[1]),
                                Integer.parseInt(split[2]))
                    } catch (e: NumberFormatException) {
                        throw ParserException(source,
                                "Invalid row start location")
                    }
                } else if (split.size == 1) {
                    null
                } else {
                    throw ParserException(source, "Invalid row start")
                }
                section = project.addRow(split[0], location, source)
            }
            '>' -> {
                section?.let { it.end() }
                section = project.row(line.substring(1), source)
            }
            '~' -> {
                val split = REPLACE_SPLIT.split(line.substring(1), 2)
                if (split.size == 2) {
                    project.addReplace(split[0], split[1])
                } else {
                    throw ParserException(source, "Invalid pattern replace")
                }
            }
            else -> {
                val section = section ?: throw ParserException(source,
                        "No row or event active")
                section.add(line, source)
            }
        }
    }

    companion object {
        private val PATH_SPLIT = Pattern.compile("/")
        private val REPLACE_SPLIT = Pattern.compile(" -> ")

        fun parse(tagStructure: TagStructure): Project {
            val properties = Properties()
            val propertiesData = tagStructure.getByteArray(
                    "project.properties") ?: throw IOException(
                    "No project.properties!")
            properties.load(ByteArrayInputStream(propertiesData))
            val parser = Parser(properties)
            parser.parseStream(tagStructure, emptyArray(), "main.txt")
            parser.section?.let { it.end() }
            return parser.project
        }
    }
}
