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

import com.tobi29.minecraft.utils.clockgenerator.generator.GeneratorException
import java8.util.stream.Stream
import org.tobi29.scapes.engine.utils.stream

class RowBatch(length: Int) {
    private val commands: Array<Command?>
    private var position: Int = 0

    init {
        commands = arrayOfNulls<Command>(length)
    }

    @Throws(GeneratorException::class)
    fun stream(): Stream<Command?> {
        return stream(*commands)
    }

    fun plusAssign(vararg command: Command) {
        System.arraycopy(command, 0, commands, position, command.size)
        position += command.size
    }

    fun remaining(): Int {
        return commands.size - position
    }
}
