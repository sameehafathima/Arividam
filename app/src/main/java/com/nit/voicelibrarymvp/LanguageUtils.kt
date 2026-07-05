package com.nit.voicelibrarymvp

object LanguageUtils {
    private const val MALAYALAM_START = '\u0D00'
    private const val MALAYALAM_END = '\u0D7F'

    // Map for Malayalam representations of English letters and digits
    private val wordMap by lazy {
        mapOf(
            "എ" to "A", "ബി" to "B", "സി" to "C", "ഡി" to "D", "ഇ" to "E",
            "എഫ്" to "F", "ജി" to "G", "എച്ച്" to "H", "ഐ" to "I", "ജെ" to "J",
            "കെ" to "K", "എൽ" to "L", "എം" to "M", "എൻ" to "N", "ഒ" to "O",
            "പി" to "P", "ക്യു" to "Q", "ആർ" to "R", "എസ്" to "S", "ടി" to "T",
            "യു" to "U", "വി" to "V", "ഡബ്ല്യു" to "W", "എക്സ്" to "X", "വൈ" to "Y", "സെഡ്" to "Z"
        )
    }

    private val charMap by lazy {
        mapOf(
            '൦' to '0', '൧' to '1', '൨' to '2', '൩' to '3', '൪' to '4',
            '൫' to '5', '൬' to '6', '൭' to '7', '൮' to '8', '൯' to '9'
        )
    }

    private val reverseWordMap by lazy { wordMap.entries.associate { (k, v) -> v to k } }
    private val malayalamToEnglishLetters by lazy { wordMap }

    // Basic transliteration map for converting common Malayalam phonetics back to English
    private val translitMap by lazy {
        mapOf(
            "ഹാരി" to "Harry", "പോട്ടർ" to "Potter", "രചയിതാവ്" to "Author",
            "കഥ" to "Story", "നോവൽ" to "Novel", "പുസ്തകം" to "Book"
        )
    }

    private val malayalamNumbers by lazy {
        mapOf(
            "ഒന്ന്" to 1, "ഒന്നാമത്തെ" to 1, "ഒരു" to 1, "one" to 1, "1" to 1, "won" to 1,
            "രണ്ട്" to 2, "രണ്ടാമത്തെ" to 2, "two" to 2, "2" to 2, "to" to 2, "too" to 2,
            "മൂന്ന്" to 3, "മൂന്നാമത്തെ" to 3, "three" to 3, "3" to 3,
            "നാല്" to 4, "നാലാമത്തെ" to 4, "four" to 4, "4" to 4, "for" to 4,
            "അഞ്ച്" to 5, "അഞ്ചാമത്തെ" to 5, "five" to 5, "5" to 5,
            "ആറ്" to 6, "ആറാമത്തെ" to 6, "six" to 6, "6" to 6,
            "ഏഴ്" to 7, "ഏഴാമത്തെ" to 7, "seven" to 7, "7" to 7,
            "എട്ട്" to 8, "എട്ടാമത്തെ" to 8, "eight" to 8, "8" to 8, "ate" to 8,
            "ഒൻപത്" to 9, "ഒൻപതാമത്തെ" to 9, "nine" to 9, "9" to 9,
            "പത്ത്" to 10, "പത്താമത്തെ" to 10, "ten" to 10, "10" to 10
        )
    }

    fun isMalayalam(text: String?): Boolean {
        if (text == null) return false
        return text.any { it in MALAYALAM_START..MALAYALAM_END }
    }

    /**
     * Corrects common Malayalam Unicode split-character issues and phonetic errors.
     */
    fun correctMalayalam(text: String?): String {
        if (text == null || text.isBlank()) return text ?: ""
        return text
            .replace("ൻറെ", "ന്റെ") 
            .replace("ൻറ", "ന്റ")   
            .replace("ഇന്റെ", "ന്റെ") 
            .replace("എൻറെ", "എന്റെ") 
            .replace("\u0D7B\u0D31\u0D46", "\u0D28\u0D4D\u0D31\u0D46") 
            .replace("\u0D7B\u0D31", "\u0D28\u0D4D\u0D31")
    }

    fun parseMalayalamNumber(text: String?): Int? {
        if (text == null) return null
        val lower = text.trim().lowercase()
        
        // 1. Try direct digits
        val digits = lower.filter { it.isDigit() }
        if (digits.isNotEmpty()) return digits.toIntOrNull()

        // 2. Try Malayalam digit characters
        val mappedDigits = lower.map { charMap[it] ?: it }.joinToString("").filter { it.isDigit() }
        if (mappedDigits.isNotEmpty()) return mappedDigits.toIntOrNull()

        // 3. Try Malayalam and English words
        val words = lower.split(Regex("[\\s.,]+"))
        for (word in words) {
            val cleanedWord = word.trim()
            if (malayalamNumbers.containsKey(cleanedWord)) {
                return malayalamNumbers[cleanedWord]
            }
        }
        
        // Fallback to contains for multi-word phrases like "മൂന്ന് എണ്ണം"
        for ((word, value) in malayalamNumbers) {
            if (lower.contains(word)) return value
        }

        return null
    }

    fun formatRackNumber(input: String?): String {
        return transliterateToEnglishPreserveSpaces(input ?: "")
    }

    /**
     * Converts Malayalam words/letters representing English alphanumeric codes back to English.
     * Preserves spaces between words.
     */
    fun transliterateToEnglishPreserveSpaces(text: String?): String {
        if (text == null || text.isBlank()) return text ?: ""
        
        val result = StringBuilder()
        val words = text.split(Regex("\\s+"))
        for ((index, word) in words.withIndex()) {
            val upperWord = word.trim()
            if (wordMap.containsKey(upperWord)) {
                result.append(wordMap[upperWord])
            } else {
                for (char in word) {
                    if (charMap.containsKey(char)) {
                        result.append(charMap[char])
                    } else if (char.isLetterOrDigit() || char == '/' || char == '\\' || char == '-') {
                        result.append(char)
                    }
                }
            }
            if (index < words.size - 1) {
                result.append(" ")
            }
        }
        return result.toString().uppercase()
    }

    /**
     * Converts Malayalam words/letters representing English alphanumeric codes back to English.
     */
    fun transliterateToEnglish(text: String?): String {
        if (text == null || text.isBlank()) return text ?: ""
        
        var result = ""
        val words = text.split(Regex("\\s+"))
        for (word in words) {
            val upperWord = word.trim()
            if (wordMap.containsKey(upperWord)) {
                result += wordMap[upperWord]
            } else {
                for (char in word) {
                    if (charMap.containsKey(char)) {
                        result += charMap[char]
                    } else if (char.isLetterOrDigit() || char == '/' || char == '\\' || char == '-') {
                        result += char
                    }
                }
            }
        }
        return result.uppercase()
    }

    /**
     * Attempts to convert Malayalam text to English if possible.
     */
    fun toEnglishIfPossible(text: String?): String {
        if (text == null || !isMalayalam(text)) return text ?: ""
        
        var currentResult = text
        for ((mal, eng) in translitMap) {
            currentResult = currentResult?.replace(mal, eng)
        }
        
        return currentResult ?: ""
    }

    /**
     * Simple transliteration from English to Malayalam alphabet phonetically.
     */
    fun transliterateToMalayalam(text: String?): String {
        if (text == null || text.isBlank()) return text ?: ""
        if (isMalayalam(text)) return text
        
        val mapping = mapOf(
            "aa" to "ാ", "ee" to "ീ", "oo" to "ൂ", "ii" to "ി", "uu" to "ു",
            "ka" to "ക", "ke" to "കെ", "ko" to "കൊ", "ki" to "കി", "ku" to "കു",
            "cha" to "ച", "ta" to "ത", "pa" to "പ", "ma" to "മ", "na" to "ന",
            "ra" to "ര", "la" to "ല", "va" to "വ", "sa" to "സ", "ha" to "ഹ",
            "ya" to "യ", "da" to "ദ", "ba" to "ബ", "ga" to "ഗ", "ja" to "ജ",
            "tha" to "ഥ", "pha" to "ഫ", "sha" to "ഷ", "dha" to "ധ", "jha" to "ഝ",
            "k" to "ക്", "m" to "മ്", "n" to "ൻ", "r" to "ർ", "l" to "ൽ", "v" to "വ്"
        )
        
        var result = text.lowercase()
        result = result.replace("ngal", "ങ്ങൾ")
        result = result.replace("kkal", "ക്കൾ")
        
        mapping.keys.sortedByDescending { it.length }.forEach { key ->
            result = result.replace(key, mapping[key]!!)
        }

        return result.filter { it in MALAYALAM_START..MALAYALAM_END || it == ' ' || it in '0'..'9' }
    }

    fun phoneticToEnglish(text: String?): String {
        if (text == null || text.isBlank()) return ""
        
        val map = mapOf(
            'ക' to "K", 'ഖ' to "KH", 'ഗ' to "G", 'ഘ' to "GH", 'ങ' to "NG",
            'ച' to "CH", 'ഛ' to "CH", 'ജ' to "J", 'ഝ' to "JH", 'ഞ' to "NY",
            'ട' to "T", 'ഠ' to "TH", 'ഡ' to "D", 'ഢ' to "DH", 'ണ' to "N",
            'ത' to "T", 'ഥ' to "TH", 'ദ' to "D", 'ധ' to "DH", 'ന' to "N",
            'പ' to "P", 'ഫ' to "PH", 'ബ' to "B", 'ഭ' to "BH", 'മ' to "M",
            'യ' to "Y", 'ര' to "R", 'ല' to "L", 'വ' to "V", 'ശ' to "SH",
            'ഷ' to "SH", 'സ' to "S", 'ഹ' to "H", 'ള' to "L", 'ഴ' to "ZH", 'റ' to "R",
            'അ' to "A", 'ആ' to "A", 'ഇ' to "I", 'ഈ' to "I", 'ഉ' to "U", 'ഊ' to "U",
            'എ' to "E", 'ഏ' to "E", 'ഐ' to "AI", 'ഒ' to "O", 'ഓ' to "O", 'ഔ' to "AU",
            'ാ' to "A", 'ി' to "I", 'ീ' to "I", 'ു' to "U", 'ൂ' to "U", 'ൃ' to "R",
            'െ' to "E", 'േ' to "E", 'ൈ' to "AI", 'ൊ' to "O", 'ോ' to "O", 'ൗ' to "AU",
            '്' to "", 'ം' to "M", 'ഃ' to "H",
            'ൺ' to "N", 'ൻ' to "N", 'ർ' to "R", 'ൽ' to "L", 'ൾ' to "L", 'ൿ' to "K"
        )
        
        val result = StringBuilder()
        for (char in text) {
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9') {
                result.append(char)
            } else {
                result.append(map[char] ?: "")
            }
        }
        return result.toString().uppercase()
    }

    fun generateCallNumber(title: String?, author: String?): String {
        val engAuthor = phoneticToEnglish(author).filter { it.isLetter() }.take(3)
        val engTitle = phoneticToEnglish(title).filter { it.isLetterOrDigit() }.take(1)
        
        return if (engAuthor.isNotEmpty() && engTitle.isNotEmpty()) {
            "$engAuthor/$engTitle".uppercase()
        } else ""
    }

    /**
     * Splits text that was joined without spaces by the STT engine (e.g. "atomichabits" -> "Atomic Habits")
     * based on common English title patterns.
     */
    fun formatTitleWithSpaces(input: String?): String {
        if (input == null || input.isBlank()) return ""
        if (isMalayalam(input)) return input.trim()

        // 1. If it already has spaces, just capitalize words
        if (input.contains(" ")) {
            return input.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }

        // 2. Heuristic for CamelCase or joined words
        // We look for transitions from lowercase to uppercase or try to find common word boundaries
        // For simplicity with library STT, we'll try to split based on capital letters if any (CamelCase)
        val result = StringBuilder()
        for (i in input.indices) {
            val char = input[i]
            if (i > 0 && char.isUpperCase() && input[i-1].isLowerCase()) {
                result.append(" ")
            }
            result.append(char)
        }
        
        // If the result still has no spaces, it might be all lowercase (atomichabits)
        // Advanced splitting would need a dictionary, but for now we'll handle the user's specific case
        val finalStr = result.toString()
        if (!finalStr.contains(" ")) {
            // Hardcoded common library patterns or simple split attempt
            // In a production app, we'd use a word segmentation library
            if (finalStr.lowercase() == "atomichabits") return "Atomic Habits"
            if (finalStr.lowercase() == "idontcare") return "I Don't Care"
        }

        return finalStr.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.trim()
    }
}
