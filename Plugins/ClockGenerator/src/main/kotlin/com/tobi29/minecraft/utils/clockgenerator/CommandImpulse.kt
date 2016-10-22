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

import org.tobi29.amboss.util.block.Block
import org.tobi29.amboss.util.block.BlockCommand
import com.tobi29.minecraft.utils.clockgenerator.generator.Generator
import com.tobi29.minecraft.utils.clockgenerator.generator.GeneratorException
import org.tobi29.amboss.util.block.CommandType
import org.tobi29.scapes.engine.utils.math.Face

class CommandImpulse(source: Source) : Command("", source) {

    @Throws(GeneratorException::class)
    override fun place(generator: Generator,
                       blocks: Array<Array<Array<Block?>>>,
                       x: Int,
                       y: Int,
                       z: Int,
                       face: Face) {
        blocks[x][y][z] = BlockCommand("", Face.EAST, CommandType.NORMAL, false,
                false)
    }
}
