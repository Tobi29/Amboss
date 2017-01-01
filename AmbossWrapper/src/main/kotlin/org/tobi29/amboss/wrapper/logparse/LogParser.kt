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

import org.tobi29.scapes.engine.utils.toArray
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class LogParser {
    private val processors = ArrayList<Pair<Array<Pattern>, (List<Matcher>) -> Unit>>()
    private var laterQueue = ArrayList<(String) -> Unit>(0)

    fun addProcessor(pattern: String,
                     processor: (Matcher) -> Unit) {
        addProcessor(arrayOf(Pattern.compile(pattern))) { processor(it[0]) }
    }

    fun addProcessor(pattern1: String,
                     pattern2: String,
                     vararg patterns: String,
                     processor: (List<Matcher>) -> Unit) {
        addProcessor(sequenceOf(pattern1, pattern2, *patterns).map {
            Pattern.compile(it)
        }.toArray(), processor)
    }

    fun addProcessor(patterns: Array<Pattern>,
                     processor: (List<Matcher>) -> Unit) {
        processors.add(Pair(patterns, processor))
    }

    fun parseMessage(str: String) {
        val later = laterQueue
        laterQueue = ArrayList<(String) -> Unit>(0)
        later.forEach { it(str) }
        processors.forEach {
            parseMatchingLine(str, it, ArrayList(it.first.size))
        }
    }

    fun parseMatchingLine(str: String,
                          pattern: Pair<Array<Pattern>, (List<Matcher>) -> Unit>,
                          matchers: MutableList<Matcher>) {
        assert(matchers.size < pattern.first.size)
        val matcher = pattern.first[matchers.size].matcher(str)
        if (matcher.matches()) {
            matchers.add(matcher)
            if (matchers.size >= pattern.first.size) {
                pattern.second(matchers)
            } else {
                laterQueue.add { parseMatchingLine(it, pattern, matchers) }
            }
        }
    }

    companion object {
        private val MATCH_LINE = Pattern.compile("\\[(.*)\\] \\[(.*)\\]: (.*)")

        fun parseLine(str: String): String? {
            val matcher = MATCH_LINE.matcher(str)
            if (!matcher.matches()) {
                return null
            }
            return matcher.group(3)
        }
    }
}
