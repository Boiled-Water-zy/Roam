package com.roami.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.drawable.Icon
import java.net.URLEncoder

enum class TaskNoticeState { WAITING, FINISHED, FAILED }

object TaskNotification {
    fun id(session: String): Int = session.hashCode()

    fun build(ctx: Context, base: String, session: String, title: String, body: String,
              state: TaskNoticeState, actions: List<String> = emptyList(), retry: Boolean = false,
              capsule: Boolean = false, prompt: String = "", choices: List<TaskChoice> = emptyList()): Notification {
        val open = PendingIntent.getActivity(ctx, (base + session).hashCode(),
            Intent(ctx, WebActivity::class.java)
                .putExtra("url", base)
                .putExtra("path", "/#/inbox/" + URLEncoder.encode(session, "UTF-8"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val actionIcon = Icon.createWithResource(ctx, R.drawable.ic_stat)
        val live = capsule || state == TaskNoticeState.WAITING
        val heading = ctx.getString(when (state) {
            TaskNoticeState.WAITING -> R.string.event_waiting_title
            TaskNoticeState.FINISHED -> R.string.event_finished_title
            TaskNoticeState.FAILED -> R.string.event_failed_title
        }, title)
        val detail = if (retry) ctx.getString(R.string.event_action_failed, body) else body
        val builder = Notification.Builder(ctx, if (capsule) EventService.CH_CAPSULE else EventService.CH_EVENTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(heading)
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setAutoCancel(!live)
            .setOngoing(live)
            .setOnlyAlertOnce(live && !retry)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setPriority(Notification.PRIORITY_HIGH)
        if (capsule && Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText(ctx.getString(when (state) {
            TaskNoticeState.WAITING -> R.string.event_waiting_chip
            TaskNoticeState.FINISHED -> R.string.event_finished_chip
            TaskNoticeState.FAILED -> R.string.event_failed_chip
        }))
        if (state == TaskNoticeState.WAITING && prompt.isNotBlank()) for (choice in choices.take(if (choices.size > 3) 2 else 3)) {
            val respond = PendingIntent.getBroadcast(ctx, (base + session + prompt + choice.value).hashCode(),
                Intent(ctx, ActionReceiver::class.java)
                    .putExtra("base", base).putExtra("session", session)
                    .putExtra("prompt", prompt).putExtra("choice", choice.value)
                    .putExtra("title", title).putExtra("body", body)
                    .putExtra("choices", TaskChoice.encode(choices)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(actionIcon, choice.label(ctx), respond).build())
        }
        if (state == TaskNoticeState.WAITING && choices.size > 3) {
            builder.addAction(Notification.Action.Builder(actionIcon, ctx.getString(R.string.event_all_choices), open).build())
        }
        if (state == TaskNoticeState.WAITING && prompt.isBlank()) for (action in actions) {
            val key = when (action) { "allow" -> "Enter"; "deny" -> "Escape"; else -> continue }
            val respond = PendingIntent.getBroadcast(ctx, (base + session + action).hashCode(),
                Intent(ctx, ActionReceiver::class.java)
                    .putExtra("base", base).putExtra("session", session).putExtra("key", key)
                    .putExtra("title", title).putExtra("body", body),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(actionIcon,
                ctx.getString(if (action == "allow") R.string.act_allow else R.string.act_deny), respond).build())
        }
        if (state == TaskNoticeState.FAILED) {
            val dismiss = PendingIntent.getBroadcast(ctx, (base + session + "dismiss").hashCode(),
                Intent(ctx, ActionReceiver::class.java).putExtra("base", base).putExtra("session", session).putExtra("dismiss", true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(actionIcon, ctx.getString(R.string.event_acknowledge), dismiss).build())
        }
        val notification = builder.build()
        notification.extras.putString("roami.session", session)
        notification.extras.putString("roami.taskState", state.name)
        notification.extras.putString("roami.prompt", prompt)
        if (capsule && Build.VERSION.SDK_INT >= 36) {
            notification.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        return notification
    }
}
