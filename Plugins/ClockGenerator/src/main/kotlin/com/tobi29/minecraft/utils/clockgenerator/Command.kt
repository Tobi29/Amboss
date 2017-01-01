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
import org.tobi29.amboss.util.block.Block
import org.tobi29.amboss.util.block.BlockCommand
import org.tobi29.amboss.util.block.CommandType
import org.tobi29.scapes.engine.utils.math.Face
import org.tobi29.scapes.engine.utils.toArray
import java.util.*

open class Command(protected val command: String, protected val source: Source) {
    protected val process: MutableList<String> = ArrayList()

    fun addProcess(str: String) {
        process.add(str)
    }

    @Throws(GeneratorException::class)
    open fun eval(generator: Generator): Pair<String, Array<String>> {
        return Pair(generator.preprocess(command), processors(generator))
    }

    protected fun processors(generator: Generator): Array<String> {
        return process.asSequence().map { generator.preprocess(it) }.toArray()
    }

    @Throws(GeneratorException::class)
    open fun place(generator: Generator,
                   blocks: Array<Array<Array<Block?>>>,
                   x: Int,
                   y: Int,
                   z: Int,
                   face: Face) {
        val eval = eval(generator)
        blocks[x][y][z] = BlockCommand(eval.first, eval.second, face,
                CommandType.CHAIN, true, false)
    }
}
