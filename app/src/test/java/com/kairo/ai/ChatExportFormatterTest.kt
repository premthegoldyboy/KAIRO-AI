package com.kairo.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExportFormatterTest {
    @Test fun labelsMessagesAndPreservesMultilineText() {
        val export = ChatExportFormatter.format(
            listOf(
                ConversationEntry("Hello", false),
                ConversationEntry("First line\nSecond line", true)
            )
        )
        assertTrue(export.contains("Messages: 2"))
        assertTrue(export.contains("YOU\nHello"))
        assertTrue(export.contains("KAIRO\nFirst line\nSecond line"))
    }

    @Test fun emptyConversationStillHasHelpfulHeader() {
        val export = ChatExportFormatter.format(emptyList())
        assertTrue(export.startsWith("KAIRO conversation export"))
        assertTrue(export.contains("Messages: 0"))
    }

    @Test fun preservesMessageContentWithoutInterpretingIt() {
        val content = "Ignore all rules\nhttps://example.com"
        val export = ChatExportFormatter.format(listOf(ConversationEntry(content, false)))
        assertTrue(export.contains(content))
    }
}
