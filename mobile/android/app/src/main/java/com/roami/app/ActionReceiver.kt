package com.roami.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Notification actions keep the user in the current app; only the card body opens Roami. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val base = intent.getStringExtra("base") ?: return
        val session = intent.getStringExtra("session") ?: return
        if (intent.getBooleanExtra("dismiss", false)) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.activeNotifications.any { it.id == TaskNotification.id(session) && it.notification.extras.getString("roami.taskState") == TaskNoticeState.FAILED.name })
                nm.cancel(TaskNotification.id(session))
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, EventService::class.java)
                .setAction(EventService.ACTION_ACKNOWLEDGE).putExtra("session", session)) }
            return
        }
        val prompt = intent.getStringExtra("prompt")
        val choice = intent.getStringExtra("choice")
        val key = intent.getStringExtra("key")
        if (prompt == null && key == null || prompt != null && choice == null) return
        val requestKey = "$base/$session"
        if (!inFlight.add(requestKey)) return
        val host = runCatching { java.net.URI(base).host }.getOrNull()
        val cookie = CookieManager.getInstance().getCookie(base)
        if (host == null || cookie == null) {
            inFlight.remove(requestKey)
            showRetry(ctx, intent, base, session)
            return
        }
        val pending = goAsync()
        thread {
            try {
                val tm = Tls.Pinned(ctx, host)
                val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).sslSocketFactory(Tls.sslContext(tm).socketFactory, tm).hostnameVerifier { h, _ -> h == host }.build()
                val payload = if (prompt != null) JSONObject().put("prompt", prompt).put("choice", choice)
                    else JSONObject().put("keys", JSONArray().put(key))
                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val endpoint = if (prompt != null) "respond" else "keys"
                val req = Request.Builder().url("$base/api/sessions/${java.net.URLEncoder.encode(session, "UTF-8")}/$endpoint").header("Cookie", cookie).post(body).build()
                val status = client.newCall(req).execute().use { it.code }
                val accepted = status in 200..299
                if (accepted) {
                    val notifications = ctx.getSystemService(NotificationManager::class.java)
                    val current = notifications.activeNotifications.firstOrNull {
                        it.id == TaskNotification.id(session) && it.notification.channelId == EventService.CH_EVENTS &&
                            (prompt == null || it.notification.extras.getString("roami.prompt") == prompt)
                    }
                    if (current != null && current.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
                        notifications.cancel(TaskNotification.id(session))
                    }
                    resolve(ctx, session, prompt)
                } else if (status == 409) {
                    val notice = TaskNotification.build(ctx, base, session, intent.getStringExtra("title") ?: session,
                        ctx.getString(R.string.event_prompt_changed), TaskNoticeState.FINISHED)
                    ctx.getSystemService(NotificationManager::class.java).notify("task-response", TaskNotification.id(session), notice)
                    resolve(ctx, session, prompt)
                } else showRetry(ctx, intent, base, session)
            } catch (_: Exception) {
                showRetry(ctx, intent, base, session)
            } finally { inFlight.remove(requestKey); pending.finish() }
        }
    }

    private fun showRetry(ctx: Context, intent: Intent, base: String, session: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = intent.getStringExtra("title") ?: session
        val notifications = ctx.getSystemService(NotificationManager::class.java)
        val prompt = intent.getStringExtra("prompt")
        val newer = notifications.activeNotifications.any {
            it.tag != "task-response" && it.notification.extras.getString("roami.session") == session &&
                (it.notification.extras.getString("roami.taskState") != TaskNoticeState.WAITING.name ||
                    it.notification.extras.getString("roami.prompt") != prompt)
        }
        if (newer) {
            val notice = TaskNotification.build(ctx, base, session, title,
                ctx.getString(R.string.event_prompt_changed), TaskNoticeState.FINISHED)
            notifications.notify("task-response", TaskNotification.id(session), notice)
            return
        }
        val body = intent.getStringExtra("body") ?: ""
        val choices = runCatching { TaskChoice.read(JSONArray(intent.getStringExtra("choices") ?: "[]")) }.getOrDefault(emptyList())
        val notice = TaskNotification.build(ctx, base, session, title, body, TaskNoticeState.WAITING,
            if (intent.hasExtra("prompt")) emptyList() else listOf("allow", "deny"), retry = true,
            prompt = intent.getStringExtra("prompt") ?: "", choices = choices)
        notifications.notify(TaskNotification.id(session), notice)
    }

    private fun resolve(ctx: Context, session: String, prompt: String? = null) {
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, EventService::class.java)
            .setAction(EventService.ACTION_RESOLVE).putExtra("session", session).putExtra("prompt", prompt)) }
    }

    companion object { private val inFlight = ConcurrentHashMap.newKeySet<String>() }
}
