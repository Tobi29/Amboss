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

class CommandRowTrigger(command: String, private val name: String, source: Source) : Command(
        command, source) {

    @Throws(GeneratorException::class)
    override fun eval(generator: Generator): Pair<String, Array<String>> {
        val row = generator.getRow(name, source)
        val coords = "${row.x} ${row.y} ${row.z}"
        val command = super.eval(generator)
        return Pair(
                command.first + " /fill " + coords + ' ' + coords +
                        " minecraft:redstone_block 0 minecraft:stone 0",
                command.second)
    }
}
