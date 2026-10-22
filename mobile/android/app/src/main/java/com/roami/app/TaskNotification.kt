package com.roami.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.URLEncoder

enum class TaskNoticeState { RUNNING, WAITING, FINISHED }

object TaskNotification {
    fun id(session: String): Int = session.hashCode()

    fun build(ctx: Context, base: String, session: String, title: String, body: String,
              state: TaskNoticeState, actions: List<String> = emptyList(), retry: Boolean = false): Notification {
        val open = PendingIntent.getActivity(ctx, id(session),
            Intent(ctx, WebActivity::class.java)
                .putExtra("path", "/#/inbox/" + URLEncoder.encode(session, "UTF-8"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val live = state != TaskNoticeState.FINISHED
        val heading = if (state == TaskNoticeState.WAITING) ctx.getString(R.string.event_waiting_title, title) else title
        val detail = if (retry) ctx.getString(R.string.event_action_failed, body) else body
        val builder = NotificationCompat.Builder(ctx, EventService.CH_EVENTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(heading)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setAutoCancel(!live)
            .setOngoing(live)
            .setOnlyAlertOnce(live && !retry)
            .setSilent(state == TaskNoticeState.RUNNING)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (state == TaskNoticeState.WAITING) for (action in actions) {
            val key = when (action) { "allow" -> "Enter"; "deny" -> "Escape"; else -> continue }
            val respond = PendingIntent.getBroadcast(ctx, (session + action).hashCode(),
                Intent(ctx, ActionReceiver::class.java)
                    .putExtra("base", base).putExtra("session", session).putExtra("key", key)
                    .putExtra("title", title).putExtra("body", body),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, ctx.getString(if (action == "allow") R.string.act_allow else R.string.act_deny), respond)
        }
        val notification = builder.build()
        if (live && Build.VERSION.SDK_INT >= 36) {
            notification.extras.putBoolean("android.requestPromotedOngoing", true)
            notification.extras.putCharSequence("android.shortCriticalText", ctx.getString(
                if (state == TaskNoticeState.WAITING) R.string.event_waiting_chip else R.string.event_running_chip))
        }
        return notification
    }
}
