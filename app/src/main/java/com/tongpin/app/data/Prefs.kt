package com.tongpin.app.data

import android.content.Context
import android.os.Build
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tongpin_prefs", Context.MODE_PRIVATE)

    var nickname: String
        get() {
            val existing = sp.getString(KEY_NICKNAME, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = NICKNAMES.random() + " " + (10..99).random()
            sp.edit().putString(KEY_NICKNAME, generated).apply()
            return generated
        }
        set(value) {
            sp.edit().putString(KEY_NICKNAME, value.trim().take(20)).apply()
        }

    var serverUrl: String
        get() {
            val existing = sp.getString(KEY_SERVER, null)
            if (existing != null) return existing
            return if (isEmulator()) "http://10.0.2.2:8080" else ""
        }
        set(value) {
            sp.edit().putString(KEY_SERVER, value.trim().trimEnd('/')).apply()
        }

    val deviceId: String
        get() {
            val existing = sp.getString(KEY_DEVICE, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = UUID.randomUUID().toString()
            sp.edit().putString(KEY_DEVICE, generated).apply()
            return generated
        }

    var seenWelcome: Boolean
        get() = sp.getBoolean(KEY_SEEN_WELCOME, false)
        set(value) = sp.edit().putBoolean(KEY_SEEN_WELCOME, value).apply()

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true)
    }

    companion object {
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_SERVER = "server_url"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_SEEN_WELCOME = "seen_welcome"

        private val NICKNAMES = listOf(
            "晚风", "橘子海", "耳机分你", "小满", "北岛", "日落电台",
            "一颗蓝莓", "在听", "慢半拍", "春日部", "下雨天", "第七首歌",
        )
    }
}
