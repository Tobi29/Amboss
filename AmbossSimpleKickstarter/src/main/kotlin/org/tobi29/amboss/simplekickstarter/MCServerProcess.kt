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

package org.tobi29.amboss.simplekickstarter

import mu.KLogging
import org.tobi29.scapes.engine.utils.io.filesystem.FilePath
import java.io.File
import java.util.regex.Pattern

class MCServerProcess(private val command: String, private val path: FilePath) {
    var process: Process? = null

    @Synchronized fun start() {
        if (process != null) {
            return
        }
        logger.info { "Starting server: $command" }
        process = ProcessBuilder(*SPLIT_COMMAND.split(command)).directory(
                File(path.toUri())).inheritIO().start()

    }

    @Synchronized fun stop() {
        logger.info { "Stopping server!" }
        process?.destroy()
        process = null
    }

    @Synchronized fun restart() {
        stop()
        start()
    }

    companion object : KLogging() {
        val SPLIT_COMMAND: Pattern = Pattern.compile(" ")
    }
}
