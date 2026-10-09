package dev.setpocket

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 48.dp, 24.dp, 32.dp); setBackgroundColor(Color.rgb(46, 52, 64))
        }
        fun text(value: String, size: Float) {
            root.addView(TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(236, 239, 244)); setPadding(0, 12.dp, 0, 12.dp) })
        }
        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        text("SET POCKET", 28f)
        text("カードの配色", 19f)
        val preview = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val images = listOf(0, 40, 80).map { card ->
            ImageView(this).apply {
                contentDescription = CardArt.description(card)
                preview.addView(this, LinearLayout.LayoutParams(0, 76.dp, 1f))
            }
        }
        root.addView(preview)
        val fields = Preferences.hexColors(this).mapIndexed { i, hex ->
            EditText(this).apply {
                setSingleLine(true); setText(hex); hint = "色${i + 1}：#RRGGBB"
                contentDescription = "カードの色${i + 1}、16進カラーコード"
                setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                root.addView(this)
            }
        }
        fun updatePreview(colors: List<String>) {
            val ints = colors.map(Color::parseColor).toIntArray()
            listOf(0, 40, 80).forEachIndexed { i, card -> images[i].setImageBitmap(CardArt.bitmap(card, false, false, ints)) }
        }
        updatePreview(Preferences.hexColors(this))
        fun preset(colors: List<String>) { fields.forEachIndexed { i, field -> field.setText(colors[i]) }; updatePreview(colors) }
        button("Nord — 赤・緑・青") { preset(Preferences.nord) }
        button("Rosé Pine — ピンク・金・青") { preset(Preferences.rose) }
        text("好きな3色を #RRGGBB で指定できます。「設定を保存」で配色をすぐに反映します。", 13f)
        text("次のゲームのモード", 19f)
        val modes = RadioGroup(this)
        val normal = RadioButton(this).apply { id = android.view.View.generateViewId(); text = "通常：81枚の山札、終了あり"; setTextColor(Color.WHITE) }
        val endless = RadioButton(this).apply { id = android.view.View.generateViewId(); text = "無限練習：常に SET のある12枚"; setTextColor(Color.WHITE) }
        val completion = RadioButton(this).apply { id = android.view.View.generateViewId(); text = "あと1枚：2枚から残り1枚を選ぶ"; setTextColor(Color.WHITE) }
        modes.addView(normal); modes.addView(endless); modes.addView(completion); root.addView(modes)
        modes.check(when (Preferences.mode(this)) { Mode.NORMAL -> normal.id; Mode.ENDLESS -> endless.id; Mode.COMPLETION -> completion.id })
        text("モード変更は、ホーム画面の「新規」を押したときに適用します。今の盤面とスコアはそのままです。通常モードの新規ゲームは0点から始まります。", 13f)
        text("あと1枚の難易度", 19f)
        val difficulty = RadioGroup(this)
        val choiceValues = listOf(6, 12, 18, 0)
        val choiceButtons = listOf("6候補", "12候補", "18候補", "自由回答：色・形・個数・塗りを指定").map { label ->
            RadioButton(this).apply { id = android.view.View.generateViewId(); text = label; setTextColor(Color.WHITE); difficulty.addView(this) }
        }
        difficulty.check(choiceButtons[choiceValues.indexOf(Preferences.choices(this))].id)
        root.addView(difficulty)
        text("候補には正解に近いカードを含めます。自由回答では候補が出ません。難易度変更は保存後、ウィジェットの「次へ」から適用します。", 13f)
        button("設定を保存") {
            val values = fields.map { it.text.toString().trim().uppercase() }
            if (values.any { !Regex("#[0-9A-F]{6}").matches(it) }) {
                fields.forEachIndexed { i, field -> if (!Regex("#[0-9A-F]{6}").matches(values[i])) field.error = "例：#88C0D0" }
            } else if (values.distinct().size != 3) {
                Toast.makeText(this, "3色は異なる色を指定してください", Toast.LENGTH_LONG).show()
            } else {
                Preferences.save(this, values, when (modes.checkedRadioButtonId) { normal.id -> Mode.NORMAL; completion.id -> Mode.COMPLETION; else -> Mode.ENDLESS }, choiceValues[choiceButtons.indexOfFirst { it.id == difficulty.checkedRadioButtonId }])
                updatePreview(values); SetWidget.refreshAll(this)
                Toast.makeText(this, "保存しました。モードは次の「新規」から適用", Toast.LENGTH_LONG).show()
            }
        }
        button("ホーム画面に追加") {
            val manager = getSystemService(AppWidgetManager::class.java)
            if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(this, SetWidget::class.java), null, null)
            else Toast.makeText(this, "ホーム画面を長押し → ウィジェット → SET Pocket", Toast.LENGTH_LONG).show()
        }
        text("遊び方", 19f)
        text("色・形・個数・塗りがそれぞれ「全部同じ」か「全部違う」3枚をタップ。白い枠は選択、金色はヒントです。\n\n通常：SET がないときに「＋3枚」。12枚を超える間は正解しても補充しません。山札が尽き、盤面にも SET がなくなると終了です。1人用の練習なので、誤答の減点はありません。\n\nあと1枚：上のお題2枚に合う1枚を6・12・18候補からタップ。自由回答では属性を指定して「回答」。正解すると次の問題へ進み、誤答は同じ問題を続けます。「次へ」はスキップです。\n\nウィジェット上部の文字から、この設定画面を開けます。カードが増えたらウィジェットを縦に広げると見やすくなります。\n\nオフライン・自動保存。SET with friends への接続はありません。", 14f)
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
