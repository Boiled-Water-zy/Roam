package com.roami.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.thread

/** 通知栏上的 允许 / 拒绝：直接往会话里发 Enter / Escape，和网页版 Service Worker 做的一样。 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val base = intent.getStringExtra("base") ?: return
        val session = intent.getStringExtra("session") ?: return
        val key = intent.getStringExtra("key") ?: return
        val nid = intent.getIntExtra("nid", 0)
        ctx.getSystemService(NotificationManager::class.java).cancel(nid)
        val host = runCatching { java.net.URI(base).host }.getOrNull() ?: return
        val cookie = CookieManager.getInstance().getCookie(base) ?: return
        val pending = goAsync()
        thread {
            try {
                val tm = Tls.Pinned(ctx, host)
                val client = OkHttpClient.Builder().sslSocketFactory(Tls.sslContext(tm).socketFactory, tm).hostnameVerifier { h, _ -> h == host }.build()
                val body = """{"keys":["$key"]}""".toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$base/api/sessions/${java.net.URLEncoder.encode(session, "UTF-8")}/keys").header("Cookie", cookie).post(body).build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
            } finally { pending.finish() }
        }
    }
}
