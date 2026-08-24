package com.example.autotapper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * 从 GitHub Releases 检查最新版本。
 *
 * 安全约束：请求服务端前会校验 URL（仅 http/https），并解析 host 拒绝
 * localhost / 环回 / 私有 / 保留地址；解析失败或命中受限地址时拒绝请求。
 */
object UpdateChecker {

    private const val REPO_OWNER = "YangZiyueZY"
    private const val REPO_NAME = "AutoTapper"
    private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val RELEASE_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseUrl: String,
        val notes: String?
    )

    private var checking = false

    /**
     * 在后台线程检查更新，结果通过 [onResult] 回调（调用方负责切回主线程）。
     * 网络失败、版本解析失败或响应不是最新版本之外的状态时返回 null。
     */
    fun checkForUpdate(onResult: (UpdateInfo?) -> Unit) {
        if (checking) {
            return
        }
        checking = true
        thread(name = "update-check") {
            val info = fetchLatestRelease()
            checking = false
            onResult(info)
        }
    }

    /**
     * 校验服务端 URL：仅允许 http/https，且解析出的地址不能是
     * localhost、环回、私有、链路本地或保留地址。
     */
    fun isSafeServerUrl(url: String): Boolean {
        val uri = try {
            Uri.parse(url).takeIf { it.isAbsolute }
        } catch (_: Exception) {
            null
        } ?: return false

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return false
        }
        val host = uri.host ?: return false
        if (host.equals("localhost", ignoreCase = true)) {
            return false
        }

        return try {
            InetAddress.getAllByName(host).all(::isAddressAllowed)
        } catch (_: UnknownHostException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun isAddressAllowed(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress ||
            addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isMulticastAddress
        ) {
            return false
        }
        val raw = addr.address ?: return true
        if (raw.size == 4) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            val b2 = raw[2].toInt() and 0xFF
            if (b0 == 0 || b0 == 127) return false                    // 0.0.0.0/8, 127.0.0.0/8
            if (b0 == 100 && b1 in 64..127) return false              // 100.64.0.0/10
            if (b0 == 169 && b1 == 254) return false                  // 169.254.0.0/16
            if (b0 == 172 && b1 in 16..31) return false               // 172.16.0.0/12
            if (b0 == 192 && b1 == 168) return false                  // 192.168.0.0/16
            if (b0 == 192 && b1 == 0 && b2 == 2) return false         // 192.0.2.0/24
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false     // 198.18.0.0/15
            if (b0 == 198 && b1 == 51 && b2 == 100) return false      // 198.51.100.0/24
            if (b0 == 203 && b1 == 0 && b2 == 113) return false       // 203.0.113.0/24
            if (b0 in 240..255) return false                          // 240.0.0.0/4
        }
        return true
    }

    private fun fetchLatestRelease(): UpdateInfo? {
        if (!isSafeServerUrl(API_URL)) {
            return null
        }
        return try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "AutoTapper-Android")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return null
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseRelease(body)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRelease(jsonText: String): UpdateInfo? {
        return try {
            val root = JSONObject(jsonText)
            val tagName = root.optString("tag_name")
            val htmlUrl = root.optString("html_url").ifBlank { RELEASE_PAGE_URL }
            val body = root.optString("body")

            var downloadUrl = htmlUrl
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk") && url.isNotBlank()) {
                        downloadUrl = url
                        break
                    }
                }
            }

            val version = extractVersion(tagName, body) ?: return null
            UpdateInfo(
                latestVersion = version,
                downloadUrl = downloadUrl,
                releaseUrl = htmlUrl,
                notes = body.ifBlank { null }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVersion(tagName: String, body: String): String? {
        body.lineSequence().forEach { line ->
            val match = Regex("""(?i)^\s*version\s*[:：]\s*(.+?)\s*$""").find(line)
            if (match != null) {
                val candidate = match.groupValues[1]
                if (parseVersion(candidate) != null) {
                    return candidate
                }
            }
        }
        val tagMatch = Regex("""\d+(?:\.\d+)+""").find(tagName)
        return tagMatch?.value?.takeIf { parseVersion(it) != null }
    }

    /** 仅比较版本大小，latest 大于 current 视为有新版本。 */
    fun isNewer(latest: String, current: String): Boolean = compareVersions(latest, current) > 0

    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        if (pa == null || pb == null) {
            return a.compareTo(b)
        }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) {
                return x.compareTo(y)
            }
        }
        return 0
    }

    private fun parseVersion(value: String): List<Int>? {
        val parts = value.trim().split('.')
        if (parts.isEmpty() || parts.size > 4) {
            return null
        }
        val numbers = mutableListOf<Int>()
        for (part in parts) {
            val num = part.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            numbers.add(num)
        }
        return numbers
    }
}

/** 发现新版本后通过系统通知推送提醒，点击跳转发布页。 */
object UpdateNotifier {

    const val CHANNEL_ID = "update_channel"
    const val NOTIFICATION_ID = 1001

    fun notify(context: Context, info: UpdateChecker.UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_autotapper)
            .setContentTitle(
                context.getString(R.string.update_notification_title, info.latestVersion)
            )
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
