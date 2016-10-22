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

package org.tobi29.amboss.plugin.mapplacer

import mu.KLogging
import org.tobi29.amboss.AmbossServer
import org.tobi29.amboss.connection.WrapperConnection
import org.tobi29.amboss.plugin.Plugin
import org.tobi29.amboss.plugin.event.ChatEvent
import org.tobi29.amboss.util.MCColor
import org.tobi29.amboss.util.TellrawString
import org.tobi29.amboss.util.block.*
import org.tobi29.amboss.util.tellraw
import org.tobi29.amboss.util.tellrawMessage
import org.tobi29.scapes.engine.server.ControlPanelProtocol
import org.tobi29.scapes.engine.utils.io.tag.TagStructure
import org.tobi29.scapes.engine.utils.io.tag.structure
import org.tobi29.scapes.engine.utils.math.Face
import org.tobi29.scapes.engine.utils.math.vector.Vector2i
import org.tobi29.scapes.engine.utils.math.vector.Vector3i
import org.tobi29.scapes.engine.utils.math.vector.minus
import java.util.*
import java.util.regex.Pattern

class MapPlacerPlugin(amboss: AmbossServer) : Plugin(amboss) {
    val TELLRAW_PREFIX = TellrawString("Map Placer", MCColor.dark_red)

    override fun initServer(wrapper: WrapperConnection,
                            configStructure: TagStructure) {
        val permissions = configStructure.structure("Permissions")
        wrapper.events.listener<ChatEvent>(this, 10) { event ->
            if (event.muted) {
                return@listener
            }
            if (!(permissions.getStructure(event.name)?.getBoolean(
                    "Map-Placer") ?: false)) {
                return@listener
            }
            val prefix = "!make mapplacer"
            if (event.message.startsWith(prefix)) {
                event.muted = true
                val args = event.message.substring(prefix.length)
                try {
                    val split = COMMAND_SPLIT.split(args)
                    if (split.size != 8) {
                        throw IllegalArgumentException(
                                "Invalid amount of arguments")
                    }
                    val map = split[1]
                    val redstoneX = split[2].toInt()
                    val redstoneY = split[3].toInt()
                    val redstoneZ = split[4].toInt()
                    val mapX = split[5].toInt()
                    val mapY = split[6].toInt()
                    val mapZ = split[7].toInt()
                    generate(wrapper, map, Vector3i(mapX, mapY, mapZ),
                            Vector2i(16, 16),
                            Vector3i(redstoneX, redstoneY, redstoneZ), 254,
                            "minecraft:barrier")
                } catch(e: IllegalArgumentException) {
                    wrapper.send("Command", structure {
                        setString("Command", tellraw("@a",
                                tellrawMessage(TELLRAW_PREFIX, TellrawString(
                                        "Usage: !make mapplacer <map> <redstone-x> <redstone-y> <redstone-z> <map-x> <map-y> <map-z>"))))
                    })
                }
            }
        }
    }

    private fun generate(channel: ControlPanelProtocol,
                         map: String,
                         start: Vector3i,
                         size: Vector2i,
                         place: Vector3i,
                         slices: Int,
                         fillMaterial: String) {
        val blocks = Array(size.x) {
            Array(11) {
                arrayOfNulls<Block>(size.y)
            }
        }

        val limit = size - 1
        var slice = 0
        for (z in 0..limit.y) {
            val zz = start.z + (z shl 4)
            for (x in 0..limit.x) {
                val xx = start.x + (x shl 4)
                if (x == limit.x) {
                    if (z == limit.y) {
                        blocks[x][3][z] = BlockCommand(
                                "/setblock ~-${limit.x} ~7 ~-${limit.y} minecraft:redstone_block",
                                Face.DOWN, CommandType.NORMAL, false, false)
                        blocks[x][9][z] = BlockCommand(
                                "",
                                Face.DOWN, CommandType.NORMAL, false, false)
                    } else {
                        blocks[x][3][z] = BlockCommand(
                                "/setblock ~-${limit.x} ~1 ~1 minecraft:redstone_block",
                                Face.DOWN, CommandType.NORMAL, false, false)
                        blocks[x][9][z] = BlockCommand(
                                "/setblock ~-${limit.x} ~1 ~1 minecraft:redstone_block",
                                Face.DOWN, CommandType.NORMAL, false, false)
                    }
                } else {
                    blocks[x][3][z] = BlockCommand(
                            "/setblock ~1 ~1 ~ minecraft:redstone_block",
                            Face.DOWN, CommandType.NORMAL, false, false)
                    blocks[x][9][z] = BlockCommand(
                            "/setblock ~1 ~1 ~ minecraft:redstone_block",
                            Face.DOWN, CommandType.NORMAL, false, false)
                }
                blocks[x][4][z] = BlockFiller("minecraft:stained_glass", 14)
                blocks[x][10][z] = BlockFiller("minecraft:stained_glass", 14)

                blocks[x][2][z] = BlockCommand(
                        "/spreadplayers ${xx + 8} ${zz + 8} 0 1 false @e[tag=ChunkLoader]",
                        Face.DOWN, CommandType.CHAIN, true, false)
                blocks[x][1][z] = BlockCommand(
                        "/scoreboard players set @e[type=!Player,x=${xx - 16},y=0,z=${zz - 16},dx=47,dy=255,dz=47] Kill 2",
                        Face.DOWN, CommandType.CHAIN, true, false)
                blocks[x][0][z] = BlockCommand(
                        "/fill $xx 0 $zz ${xx + 15} 127 ${zz + 15} $fillMaterial",
                        Face.DOWN, CommandType.CHAIN, true, false)

                if (slice < slices) {
                    blocks[x][8][z] = BlockCommand(
                            "/spreadplayers ${start.x + 1} ${start.z + 1 + slice} 0 1 false @e[tag=ChunkLoader]",
                            Face.DOWN, CommandType.CHAIN, true, false)
                    blocks[x][7][z] = BlockCommand(
                            "/setblock ${start.x + 1} 1 ${start.z + 1 + slice} minecraft:structure_block 0 replace {mode:\"LOAD\",posY:0,name:\"${map}_$slice\"}",
                            Face.DOWN, CommandType.CHAIN, true, false)
                    blocks[x][6][z] = BlockCommand(
                            "/setblock ${start.x + 1} 3 ${start.z + 1 + slice} minecraft:command_block 0 replace {Command:\"/fill ~ ~-2 ~ ~ ~ ~ $fillMaterial\"}",
                            Face.DOWN, CommandType.CHAIN, true, false)
                    blocks[x][5][z] = BlockCommand(
                            "/setblock ${start.x + 1} 2 ${start.z + 1 + slice} minecraft:redstone_block",
                            Face.DOWN, CommandType.CHAIN, true, false)
                } else {
                    blocks[x][8][z] = BlockFiller("minecraft:stained_glass", 0)
                    blocks[x][7][z] = BlockFiller("minecraft:stained_glass", 0)
                    blocks[x][6][z] = BlockFiller("minecraft:stained_glass", 0)
                    blocks[x][5][z] = BlockFiller("minecraft:stained_glass", 0)
                }

                slice++
            }
        }

        val list = ArrayList<TagStructure>(65536)
        writeBlocks(place, blocks) {
            list.add(structure { setString("Command", it) })
        }
        list.add(structure {
            setString("Command",
                    "/setblock ${place.x} ${place.y + 11} ${place.z} minecraft:structure_block 0 replace {mode:\"SAVE\",posY:-11,sizeX:${size.x},sizeY:11,sizeZ:${size.y},name:\"$map\"}")
        })
        channel.send("Command", structure { setList("Commands", list) })
    }

    companion object : KLogging() {
        private val COMMAND_SPLIT = Pattern.compile(" ")
    }
}
