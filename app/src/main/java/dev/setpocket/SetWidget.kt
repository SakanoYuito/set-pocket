package dev.setpocket

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.view.View
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.res.Configuration
import android.util.TypedValue
import android.widget.RemoteViews

class SetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it, load(context, it)) }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context, manager, id, load(context, id))
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { context.deleteSharedPreferences("widget_$it") }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val manager = AppWidgetManager.getInstance(context)
        if (id == -1 || manager.getAppWidgetInfo(id) == null) return
        val game = load(context, id)
        if (intent.getLongExtra("revision", -1) != game.revision) return
        when (intent.getStringExtra("command")) {
            "tap" -> game.tap(intent.getIntExtra("index", -1))
            "hint" -> game.showHint()
            "clear" -> game.clear()
            "new" -> game.newPuzzle(Preferences.mode(context), choices = Preferences.choices(context))
            "part" -> { val part = intent.getIntExtra("index", -1); if (part in 0..11) game.choosePart(part / 3, part % 3) }
            "submit" -> game.submitAnswer()
            "add" -> game.addThree()
            else -> return
        }
        save(context, id, game)
        render(context, manager, id, game)
    }
    companion object {
        private const val ACTION = "dev.setpocket.PLAY"
        private val cards = intArrayOf(R.id.card0, R.id.card1, R.id.card2, R.id.card3, R.id.card4, R.id.card5,
            R.id.card6, R.id.card7, R.id.card8, R.id.card9, R.id.card10, R.id.card11, R.id.card12, R.id.card13, R.id.card14, R.id.card15, R.id.card16, R.id.card17, R.id.card18, R.id.card19, R.id.card20)
        private val rows = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6)
        private val attributeRows = intArrayOf(R.id.attribute_row0, R.id.attribute_row1, R.id.attribute_row2, R.id.attribute_row3)
        private val attributes = intArrayOf(R.id.attribute0_0, R.id.attribute0_1, R.id.attribute0_2, R.id.attribute1_0, R.id.attribute1_1, R.id.attribute1_2, R.id.attribute2_0, R.id.attribute2_1, R.id.attribute2_2, R.id.attribute3_0, R.id.attribute3_1, R.id.attribute3_2)
        private fun load(context: Context, id: Int): Game {
            val p = context.getSharedPreferences("widget_$id", Context.MODE_PRIVATE)
            val board = p.getString("board", "")!!.split(',').mapNotNull { it.toIntOrNull() }
            val mode = runCatching { Mode.valueOf(p.getString("mode", "ENDLESS")!!) }.getOrDefault(Mode.ENDLESS)
            val deck = p.getString("deck", "")!!.split(',').mapNotNull { it.toIntOrNull() }
            val pair = p.getString("pair", "")!!.split(',').mapNotNull { it.toIntOrNull() }
            val choices = p.getInt("choices", 6).takeIf { it in listOf(0,6,12,18) } ?: 6
            val parts = p.getString("parts", "")!!.split(',').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 && it.all { v -> v in -1..2 } } ?: List(4) { -1 }
            val all = board + deck + pair
            val score = p.getInt("score", 0)
            val valid = p.contains("board") && board.size <= 21 && board.size % 3 == 0 &&
                all.distinct().size == all.size && all.all { it in 0..80 } &&
                (when (mode) {
                    Mode.NORMAL -> pair.isEmpty() && all.size + score * 3 == 81
                    Mode.ENDLESS -> pair.isEmpty() && board.size == 12 && Rules.findSet(board) != null
                    Mode.COMPLETION -> pair.size == 2 && deck.isEmpty() && board.size == choices && (choices == 0 || Rules.completingCard(pair[0], pair[1]) in board)
                })
            if (!valid) return Game.fresh(Preferences.mode(context), choices = Preferences.choices(context)).also { save(context, id, it) }
            return Game(board, p.getString("selected", "")!!.split(',').mapNotNull { it.toIntOrNull() }.filter { it in board.indices }.toMutableSet(),
                score, p.getInt("hint", -1), p.getLong("revision", 0), p.getString("status", "3枚タップして SET を見つけよう")!!, mode, deck, pair, choices, parts)
        }
        private fun save(context: Context, id: Int, game: Game) {
            context.getSharedPreferences("widget_$id", Context.MODE_PRIVATE).edit()
                .putString("board", game.board.joinToString(",")).putString("selected", game.selected.joinToString(","))
                .putInt("score", game.score).putInt("hint", game.hint).putLong("revision", game.revision)
                .putString("status", game.status).putString("mode", game.mode.name).putString("deck", game.deck.joinToString(",")).putString("pair", game.pair.joinToString(",")).putInt("choices", game.candidateCount).putString("parts", game.answerParts.joinToString(",")).commit()
        }
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, SetWidget::class.java)).forEach { render(context, manager, it, load(context, it)) }
        }
        private fun render(context: Context, manager: AppWidgetManager, id: Int, game: Game) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            fun bind(viewId: Int, command: String, index: Int = -1) {
                val intent = Intent(context, SetWidget::class.java).setAction(ACTION)
                    .setData(Uri.parse("setpocket://widget/$id/${game.revision}/$command/$index"))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("command", command)
                    .putExtra("index", index).putExtra("revision", game.revision)
                views.setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            val colors = Preferences.colors(context)
            val completion = game.mode == Mode.COMPLETION
            val freeAnswer = completion && game.candidateCount == 0
            val rowCount = if (completion) game.board.size / 3 else maxOf(4, (game.board.size + 2) / 3)
            views.setViewVisibility(R.id.answer_form, if (freeAnswer) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.submit, if (freeAnswer) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.pair_caption, if (freeAnswer) "この2枚に合うカードの属性を指定" else "この2枚と SET になる1枚は？")
            if (freeAnswer) attributes.forEachIndexed { part, viewId ->
                val axis = part / 3
                val value = part % 3
                views.setInt(viewId, "setBackgroundResource", if (game.answerParts[axis] == value) R.drawable.selected_bg else R.drawable.button_bg)
                views.setTextColor(viewId, if (axis == 2) colors[value] else android.graphics.Color.rgb(236, 239, 244))
                views.setContentDescription(viewId, listOf("個数", "形", "色", "塗り")[axis] + "：" + (value + 1) + if (game.answerParts[axis] == value) "、選択中" else "")
                bind(viewId, "part", part)
            }
            bind(R.id.submit, "submit")
            views.setViewVisibility(R.id.pair_row, if (completion) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pair_caption, if (completion) View.VISIBLE else View.GONE)
            if (completion) {
                intArrayOf(R.id.pair0, R.id.pair1).forEachIndexed { i, viewId ->
                    views.setImageViewBitmap(viewId, CardArt.bitmap(game.pair[i], false, false, colors))
                    views.setContentDescription(viewId, "お題${i + 1}：${CardArt.description(game.pair[i])}")
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                val options = manager.getAppWidgetOptions(id)
                val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val widthKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                val heightKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                val width = options.getInt(widthKey, 300).coerceAtLeast(100).toFloat()
                val height = options.getInt(heightKey, 360).coerceAtLeast(140).toFloat()
                // Root padding 16dp; header 28dp; status 36dp; buttons 44dp.
                // Each card has 3dp margins and a fixed bitmap ratio of 220:132.
                val idealRowHeight = (((width - 16f) / 3f - 6f) * 132f / 220f) + 6f
                val formRowHeight = minOf(40f, (height - 156f - 28f) / 4f).coerceAtLeast(24f)
                if (freeAnswer) attributeRows.forEach { views.setViewLayoutHeight(it, formRowHeight, TypedValue.COMPLEX_UNIT_DIP) }
                val availableRowHeight = (height - 124f - (if (completion) 32f else 0f) - (if (freeAnswer) 4f * formRowHeight else 0f)) / (rowCount + if (completion) 1 else 0)
                val rowHeight = minOf(idealRowHeight, availableRowHeight).coerceAtLeast(8f)
                rows.forEach { views.setViewLayoutHeight(it, rowHeight, TypedValue.COMPLEX_UNIT_DIP) }
                views.setViewLayoutHeight(R.id.pair_row, rowHeight, TypedValue.COMPLEX_UNIT_DIP)
            }
            rows.forEachIndexed { i, row -> views.setViewVisibility(row, if (i < rowCount) View.VISIBLE else View.GONE) }
            cards.forEachIndexed { index, viewId ->
                views.setViewVisibility(viewId, if (index in game.board.indices) View.VISIBLE else View.INVISIBLE)
                if (index !in game.board.indices) return@forEachIndexed
                views.setImageViewBitmap(viewId, CardArt.bitmap(game.board[index], index in game.selected, index == game.hint, colors))
                views.setContentDescription(viewId, "${index + 1}番：${CardArt.description(game.board[index])}" + if (index in game.selected) "、選択中" else "")
                bind(viewId, "tap", index)
            }
            views.setTextViewText(R.id.title, if (game.mode == Mode.NORMAL) "${game.score} SET  ·  山札 ${game.deck.size}  ·  通常" else if (completion) "${game.score} 正解  ·  ${if (freeAnswer) "自由回答" else "${game.candidateCount}候補"}" else "${game.score} SET  ·  無限練習")
            views.setContentDescription(R.id.title, "設定を開く")
            views.setOnClickPendingIntent(R.id.title, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setViewVisibility(R.id.add_three, if (game.mode == Mode.NORMAL) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.new_game, if (completion) "次へ" else "新規")
            views.setViewVisibility(R.id.clear, if (completion) View.GONE else View.VISIBLE)
            bind(R.id.add_three, "add")
            views.setTextViewText(R.id.status, game.status)
            bind(R.id.hint, "hint"); bind(R.id.clear, "clear"); bind(R.id.new_game, "new")
            manager.updateAppWidget(id, views)
        }
    }
}
