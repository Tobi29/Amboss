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

package org.tobi29.amboss.util

import java8.util.stream.Collectors
import org.tobi29.scapes.engine.utils.io.ByteBufferStream
import org.tobi29.scapes.engine.utils.io.asString
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.json.TagStructureJSON
import org.tobi29.scapes.engine.utils.io.tag.structure
import org.tobi29.scapes.engine.utils.stream

fun tellraw(selector: String,
            message: TellrawString): String {
    val stream = ByteBufferStream()
    TagStructureJSON.write(message.write(), stream, false)
    val messageJSON = stream.buffer().asString()
    return "tellraw $selector $messageJSON"
}

fun tellrawMessage(prefix: TellrawString,
                   message: TellrawString): TellrawString {
    return TellrawString(prefix, TellrawString(" | "), message)
}

data class TellrawString(val text: String = "",
                         val color: MCColor? = null,
                         val elements: List<TellrawString>? = null) {
    constructor(vararg elements: TellrawString) : this(
            stream(*elements).collect(Collectors.toList<TellrawString>()))

    constructor(elements: List<TellrawString>) : this("", null, elements)

    fun write(): TagStructure {
        return structure {
            setString("text", text)
            color?.let { setString("color", it.toString()) }
            elements?.let {
                setList("extra",
                        it.stream().map { it.write() }.collect(
                                Collectors.toList<TagStructure>()))
            }
        }
    }
}

enum class MCColor {
    black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white
}
