package com.roami.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TaskNotificationTextTest {
    @Test fun internalBranchNamesDoNotBecomeCardTitles() {
        assertNull(TaskNotificationText.taskName("chore-sync-latest-sh", "session"))
        assertNull(TaskNotificationText.taskName("2026-1017-0937-0008", "session"))
        assertEquals("修复手机通知", TaskNotificationText.taskName("修复手机通知", "session"))
    }

    @Test fun resultPreviewStripsMarkdownLinksAndTerminalControls() {
        assertEquals("修复了通知跳转，下载 APK。", TaskNotificationText.summary(
            "\u001B[32m## **修复了通知跳转**，下载 [APK](https://example.com/api/apk)。\u001B[0m\n后续的实现细节不放进预览。"))
    }

    @Test fun briefAcknowledgementIncludesActualResult() {
        assertEquals("已完成。 修复了按钮提交和聊天定位。", TaskNotificationText.summary("已完成。\n修复了按钮提交和聊天定位。还有其他说明。"))
    }

    @Test fun longPreviewDoesNotSplitUnicodeCharacters() {
        val result = TaskNotificationText.summary("通".repeat(59) + "\uD83D\uDE80" + "后续内容")
        assertEquals(61, result.codePointCount(0, result.length))
        assertFalse(result.contains('\uFFFD'))
        assertEquals("\uD83D\uDE80…", result.takeLast(3))
    }
}
