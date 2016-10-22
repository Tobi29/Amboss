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

package org.tobi29.amboss.wrapper.logparse

import org.tobi29.amboss.wrapper.stdout
import org.tobi29.scapes.engine.utils.io.ByteBufferStream
import org.tobi29.scapes.engine.utils.math.max
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue

class LogReader(private val consumer: (String) -> Unit) : OutputStream() {
    private val buffer = ByteBufferStream()
    private val lines = ConcurrentLinkedQueue<String>()
    @Synchronized override fun write(b: Int) {
        buffer.put(b)
        scanLine(1)
    }

    @Synchronized override fun write(b: ByteArray,
                                     off: Int,
                                     len: Int) {
        buffer.put(b, off, len)
        scanLine(len)
    }

    private fun scanLine(len: Int) {
        val buffer = buffer.buffer()
        val oldPos = buffer.position()
        buffer.limit(oldPos)
        buffer.position(max(oldPos - len - ln.length, 0))
        var lastLine = 0
        while (buffer.hasRemaining()) {
            if (buffer.get() == lnChar) {
                if (buffer.remaining() + 1 >= ln.length) {
                    val position = buffer.position() - 1
                    var valid = true
                    for (i in 1..(ln.length - 1)) {
                        if (buffer.get(position + i) != ln[i].toByte()) {
                            valid = false
                            break
                        }
                    }
                    if (valid) {
                        val array = ByteArray(position - lastLine)
                        buffer.position(lastLine)
                        buffer.get(array, 0, array.size)
                        val line = String(array)
                        stdout.println(line)
                        lines.add(line)
                        lastLine = position + ln.length
                        buffer.position(lastLine)
                    }
                }
            }
        }
        buffer.position(lastLine)
        buffer.compact()
        buffer.position(oldPos - lastLine)
        while (lines.isNotEmpty()) {
            try {
                consumer(lines.poll())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        val ln = System.getProperty("line.separator")
        val lnChar = ln[0].toByte()
    }
}
