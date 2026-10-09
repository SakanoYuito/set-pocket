package dev.setpocket

import android.graphics.*

object CardArt {
    fun bitmap(card: Int, selected: Boolean, hinted: Boolean, colors: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(220, 132, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(172, 46, 52, 64)
        canvas.drawRoundRect(3f, 3f, 217f, 129f, 13f, 13f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected || hinted) 3.5f else 1f
        paint.color = when { selected -> Color.rgb(236, 239, 244); hinted -> Color.rgb(235, 203, 139); else -> Color.argb(44, 216, 222, 233) }
        canvas.drawRoundRect(4f, 4f, 216f, 128f, 14f, 14f, paint)
        val (count, shape, colour, fill) = Rules.attributes(card)
        val ink = colors[colour]
        for (i in 0..count) {
            val cx = 110f + (i - count / 2f) * 58f
            val p = Path()
            when (shape) {
                0 -> p.addOval(cx - 21, 29f, cx + 21, 103f, Path.Direction.CW)
                1 -> { p.moveTo(cx, 25f); p.lineTo(cx + 23, 66f); p.lineTo(cx, 107f); p.lineTo(cx - 23, 66f); p.close() }
                else -> {
                    p.moveTo(cx - 18, 31f)
                    p.cubicTo(cx + 20, 11f, cx + 30, 49f, cx + 13, 65f)
                    p.cubicTo(cx - 6, 83f, cx + 32, 96f, cx + 12, 102f)
                    p.cubicTo(cx - 24, 119f, cx - 31, 84f, cx - 14, 67f)
                    p.cubicTo(cx + 4, 48f, cx - 31, 43f, cx - 18, 31f)
                    p.close()
                }
            }
            paint.color = ink
            paint.strokeWidth = 3f
            if (fill == 2) { paint.style = Paint.Style.FILL; canvas.drawPath(p, paint) }
            if (fill == 1) {
                canvas.save(); canvas.clipPath(p)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                for (y in 24..108 step 8) canvas.drawLine(cx - 30, y.toFloat(), cx + 30, y.toFloat(), paint)
                canvas.restore()
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
            canvas.drawPath(p, paint)
        }
        return bitmap
    }
    fun description(card: Int): String {
        val (count, shape, colour, fill) = Rules.attributes(card)
        return "${arrayOf("色1", "色2", "色3")[colour]}・${arrayOf("楕円", "ひし形", "波形")[shape]}・${count + 1}個・${arrayOf("白抜き", "縞", "塗りつぶし")[fill]}"
    }
}
