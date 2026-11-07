package com.mouya.phirachartstudio.engine

/**
 * Small strict JSON reader used by the chart validator. Keeping this parser in
 * the pure Kotlin module avoids an Android-only dependency in the file format
 * validation path and rejects trailing data / malformed escapes before export.
 */
internal object StrictJson {
    sealed interface Value {
        data class ObjectValue(val values: Map<String, Value>) : Value
        data class ArrayValue(val values: List<Value>) : Value
        data class StringValue(val value: String) : Value
        data class NumberValue(val raw: String) : Value {
            fun doubleOrNull(): Double? = raw.toDoubleOrNull()?.takeIf { it.isFinite() }
            fun intOrNull(): Int? = doubleOrNull()?.takeIf { it % 1.0 == 0.0 && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
        }
        data class BooleanValue(val value: Boolean) : Value
        data object NullValue : Value
    }

    fun parse(source: String): Value = Parser(source).parse()

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): Value {
            skipWhitespace()
            val value = parseValue(0)
            skipWhitespace()
            require(index == source.length) { "Unexpected trailing JSON at character $index" }
            return value
        }

        private fun parseValue(depth: Int): Value {
            require(depth <= 128) { "JSON nesting is too deep" }
            skipWhitespace()
            require(index < source.length) { "Unexpected end of JSON" }
            return when (source[index]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> Value.StringValue(parseString())
                't' -> { consumeLiteral("true"); Value.BooleanValue(true) }
                'f' -> { consumeLiteral("false"); Value.BooleanValue(false) }
                'n' -> { consumeLiteral("null"); Value.NullValue }
                '-', in '0'..'9' -> Value.NumberValue(parseNumber())
                else -> error("Unexpected JSON token at character $index")
            }
        }

        private fun parseObject(depth: Int): Value.ObjectValue {
            expect('{')
            skipWhitespace()
            val result = LinkedHashMap<String, Value>()
            if (consumeIf('}')) return Value.ObjectValue(result)
            while (true) {
                skipWhitespace()
                require(index < source.length && source[index] == '"') { "Expected JSON object key" }
                val key = parseString()
                require(!result.containsKey(key)) { "Duplicate JSON key: $key" }
                skipWhitespace()
                expect(':')
                result[key] = parseValue(depth)
                skipWhitespace()
                if (consumeIf('}')) break
                expect(',')
            }
            return Value.ObjectValue(result)
        }

        private fun parseArray(depth: Int): Value.ArrayValue {
            expect('[')
            skipWhitespace()
            val result = ArrayList<Value>()
            if (consumeIf(']')) return Value.ArrayValue(result)
            while (true) {
                result += parseValue(depth)
                skipWhitespace()
                if (consumeIf(']')) break
                expect(',')
            }
            return Value.ArrayValue(result)
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < source.length) {
                val char = source[index++]
                when {
                    char == '"' -> return result.toString()
                    char == '\\' -> {
                        require(index < source.length) { "Incomplete JSON escape" }
                        when (val escaped = source[index++]) {
                            '"' -> result.append('"')
                            '\\' -> result.append('\\')
                            '/' -> result.append('/')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(index + 4 <= source.length) { "Incomplete Unicode escape" }
                                val code = source.substring(index, index + 4).toIntOrNull(16)
                                    ?: error("Invalid Unicode escape")
                                result.append(code.toChar())
                                index += 4
                            }
                            else -> error("Invalid JSON escape: $escaped")
                        }
                    }
                    char.code < 0x20 -> error("Unescaped control character in JSON string")
                    else -> result.append(char)
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseNumber(): String {
            val start = index
            consumeIf('-')
            require(index < source.length) { "Incomplete JSON number" }
            if (consumeIf('0')) {
                require(index >= source.length || source[index] !in '0'..'9') { "Leading zero in JSON number" }
            } else {
                require(source[index] in '1'..'9') { "Invalid JSON integer" }
                while (index < source.length && source[index] in '0'..'9') index++
            }
            if (consumeIf('.')) {
                require(index < source.length && source[index] in '0'..'9') { "Invalid JSON fraction" }
                while (index < source.length && source[index] in '0'..'9') index++
            }
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                index++
                if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
                require(index < source.length && source[index] in '0'..'9') { "Invalid JSON exponent" }
                while (index < source.length && source[index] in '0'..'9') index++
            }
            return source.substring(start, index)
        }

        private fun consumeLiteral(literal: String) {
            require(source.regionMatches(index, literal, 0, literal.length)) { "Invalid JSON literal" }
            index += literal.length
        }

        private fun skipWhitespace() {
            while (index < source.length && (source[index] == ' ' || source[index] == '\n' || source[index] == '\r' || source[index] == '\t')) index++
        }

        private fun expect(char: Char) {
            require(index < source.length && source[index] == char) { "Expected '$char' at character $index" }
            index++
        }

        private fun consumeIf(char: Char): Boolean {
            if (index < source.length && source[index] == char) {
                index++
                return true
            }
            return false
        }
    }
}
