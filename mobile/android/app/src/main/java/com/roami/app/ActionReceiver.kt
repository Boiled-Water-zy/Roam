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
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** 通知栏上的 允许 / 拒绝：直接往会话里发 Enter / Escape，和网页版 Service Worker 做的一样。 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val base = intent.getStringExtra("base") ?: return
        val session = intent.getStringExtra("session") ?: return
        val key = intent.getStringExtra("key") ?: return
        val requestKey = "$base/$session/$key"
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
                val client = OkHttpClient.Builder().sslSocketFactory(Tls.sslContext(tm).socketFactory, tm).hostnameVerifier { h, _ -> h == host }.build()
                val body = """{"keys":["$key"]}""".toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$base/api/sessions/${java.net.URLEncoder.encode(session, "UTF-8")}/keys").header("Cookie", cookie).post(body).build()
                val accepted = client.newCall(req).execute().use { it.isSuccessful }
                if (accepted) {
                    val notifications = ctx.getSystemService(NotificationManager::class.java)
                    val current = notifications.activeNotifications.firstOrNull {
                        it.id == TaskNotification.id(session) && it.notification.channelId == EventService.CH_EVENTS
                    }
                    if (current != null && current.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
                        notifications.cancel(TaskNotification.id(session))
                    }
                    runCatching { ctx.startService(Intent(ctx, EventService::class.java)
                        .setAction(EventService.ACTION_RESOLVE).putExtra("session", session)) }
                } else showRetry(ctx, intent, base, session)
            } catch (_: Exception) {
                showRetry(ctx, intent, base, session)
            } finally { inFlight.remove(requestKey); pending.finish() }
        }
    }

    private fun showRetry(ctx: Context, intent: Intent, base: String, session: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = intent.getStringExtra("title") ?: session
        val body = intent.getStringExtra("body") ?: ""
        val notice = TaskNotification.build(ctx, base, session, title, body, TaskNoticeState.WAITING, listOf("allow", "deny"), retry = true)
        ctx.getSystemService(NotificationManager::class.java).notify(TaskNotification.id(session), notice)
    }

    companion object { private val inFlight = ConcurrentHashMap.newKeySet<String>() }
}
