package com.roami.app

object TaskNotificationText {
    fun plainText(text: String): String = text
        .replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "")
        .replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), "")
        .replace(Regex("!?(\\[[^]\\n]+])\\([^\\n)]*\\)")) { it.groupValues[1].removeSurrounding("[", "]") }
        .replace(Regex("(?m)^\\s*```[^\\n]*$"), "")
        .replace(Regex("`([^`\\n]+)`"), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("(?m)^\\s*(?:#{1,6}\\s+|[-*]\\s+)"), "")
        .trim()

    fun summary(text: String): String {
        val clean = plainText(text).replace(Regex("\\s+"), " ")
        val sentences = clean.split(Regex("(?<=[。！？])|(?<=[.!?])\\s+"))
        var result = sentences.firstOrNull().orEmpty().trim()
        if (result.codePointCount(0, result.length) < 12 && sentences.size > 1)
            result += " " + sentences[1].trim()
        return shorten(result, 60)
    }

    fun taskName(title: String, session: String): String? {
        val clean = plainText(title).replace(Regex("\\s+"), " ")
        if (clean.isBlank() || clean == session ||
            Regex("(?i)^(?:feat|fix|chore|docs|test|refactor|perf|build|ci|style)[/:_-].*").matches(clean) ||
            Regex("^\\d{4}-\\d{4}-.*").matches(clean)) return null
        return shorten(clean, 24)
    }

    private fun shorten(text: String, limit: Int): String {
        if (text.codePointCount(0, text.length) <= limit) return text
        return text.substring(0, text.offsetByCodePoints(0, limit)).trimEnd() + "…"
    }
}
