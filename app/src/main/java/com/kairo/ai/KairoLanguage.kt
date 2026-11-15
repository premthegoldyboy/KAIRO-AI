package com.kairo.ai

enum class LangStyle { ENGLISH, TELUGU_ENGLISH, TELUGU_SCRIPT }

/** Lightweight offline language-style detection. */
object KairoLanguage {
    private val teluguBlock = '\u0C00'..'\u0C7F'
    private val tokenSplit = Regex("[^\\p{L}]+")

    private val romanizedTeluguWords = setOf(
        "enti", "entha", "enta", "entandi", "ela", "ekkada", "eppudu", "ippudu", "evaru", "enduku", "emi",
        "cheppu", "cheppandi", "cheyyi", "cheyandi", "cheyali", "cheyyali", "chey", "chestunna", "chestunnav",
        "nenu", "nuvvu", "meeru", "naaku", "ardham", "ardam", "gurinchi", "vartalu", "repu", "ninna",
        "bagundi", "baagundi", "bagunnara", "ledu", "undi", "unnaru", "unnava", "unnavu", "unnav",
        "kavali", "kaavali", "telusa", "telusu", "chala", "kuda", "anni", "ayindi", "avunu", "kaadu",
        "pettuko", "gurthupettuko", "gurtupettuko", "matladu", "namaste", "ante", "kani", "inka",
        "ikkada", "akkada", "vellali", "tinnava", "marchipo", "marichipo"
    )

    fun detect(message: String): LangStyle {
        val letters = message.count { it.isLetter() }
        if (letters == 0) return LangStyle.ENGLISH
        val teluguLetters = message.count { it in teluguBlock }
        if (teluguLetters * 5 >= letters) return LangStyle.TELUGU_SCRIPT
        val hit = message.lowercase().split(tokenSplit).any { it in romanizedTeluguWords }
        return if (hit) LangStyle.TELUGU_ENGLISH else LangStyle.ENGLISH
    }
}
