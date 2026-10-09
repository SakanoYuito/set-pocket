package dev.setpocket

import android.content.Context
import android.graphics.Color

object Preferences {
    val nord = listOf("#BF616A", "#A3BE8C", "#88C0D0")
    val rose = listOf("#EB6F92", "#F6C177", "#9CCFD8")
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun hexColors(context: Context): List<String> = (0..2).map { prefs(context).getString("color$it", nord[it])!! }
    fun colors(context: Context): IntArray = hexColors(context).map(Color::parseColor).toIntArray()
    fun mode(context: Context): Mode = runCatching { Mode.valueOf(prefs(context).getString("mode", "NORMAL")!!) }.getOrDefault(Mode.NORMAL)
    fun choices(context: Context): Int = prefs(context).getInt("choices", 6).takeIf { it in listOf(0, 6, 12, 18) } ?: 6
    fun save(context: Context, colors: List<String>, mode: Mode, choices: Int) {
        val edit = prefs(context).edit().putString("mode", mode.name).putInt("choices", choices)
        colors.forEachIndexed { i, value -> edit.putString("color$i", value) }
        edit.commit()
    }
}
