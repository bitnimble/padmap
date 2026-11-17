package app.padmap.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import app.padmap.layout.Button
import kotlin.math.max
import kotlin.math.roundToInt

/** Draws bindings as controller glyphs (face buttons, d-pad, bumpers, triggers, sticks, …) on an Android canvas. */
class GlyphPainter(private val density: Float) {
    private enum class Shape { FACE, DPAD, BUMPER, TRIGGER, STICK, PRESSED_STICK, MENU, HOME, KEY, UNBOUND }

    private data class Glyph(val shape: Shape, val button: Button?, val label: String)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = GLYPH
        textAlign = Paint.Align.CENTER
        setShadowLayer(dp(2f), 0f, 0f, Color.BLACK)
    }
    private val box = RectF()
    private val path = Path()
    private val crossbar = Path()

    /** Draws a binding centred on (cx, cy) as controller glyphs, one per part, joined by "+". */
    fun drawBinding(canvas: Canvas, parts: List<String>, style: ButtonStyle, cx: Float, cy: Float, height: Float) {
        val glyphs = parts.map { glyph(it, style) }
        val gap = height * 0.18f
        val plus = labelWidth("+", height * CHIP_TEXT)
        var x = cx - bindingWidth(parts, style, height) / 2
        glyphs.forEachIndexed { i, glyph ->
            if (i > 0) {
                drawLabel(canvas, "+", x + gap + plus / 2, cy, height * CHIP_TEXT, Palette.TEXT)
                x += plus + 2 * gap
            }
            val width = width(glyph, height)
            draw(canvas, glyph, style, x, width, cy, height)
            x += width
        }
    }

    fun bindingWidth(parts: List<String>, style: ButtonStyle, height: Float): Float {
        val joins = (parts.size - 1) * (labelWidth("+", height * CHIP_TEXT) + 2 * height * 0.18f)
        return max(0f, joins) + parts.sumOf { width(glyph(it, style), height).toDouble() }.toFloat()
    }

    private fun dp(value: Float): Float = (value * density).roundToInt().toFloat()

    private fun glyph(part: String, style: ButtonStyle): Glyph {
        Button.named(part)?.let { return Glyph(shape(it), it, style.label(it)) }
        if (part == "?") return Glyph(Shape.UNBOUND, null, part)
        return Glyph(if (part == "LS" || part == "RS") Shape.STICK else Shape.KEY, null, part)
    }

    private fun shape(button: Button): Shape = when (button) {
        Button.A, Button.B, Button.X, Button.Y -> Shape.FACE
        Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT -> Shape.DPAD
        Button.LB, Button.RB -> Shape.BUMPER
        Button.LT, Button.RT -> Shape.TRIGGER
        Button.L3, Button.R3 -> Shape.PRESSED_STICK
        Button.SELECT, Button.START -> Shape.MENU
        Button.HOME -> Shape.HOME
    }

    private fun width(glyph: Glyph, h: Float): Float = when (glyph.shape) {
        Shape.FACE, Shape.DPAD, Shape.STICK, Shape.PRESSED_STICK -> h
        Shape.HOME -> if (isHouse(glyph)) h * 0.8f else max(h * 0.8f, labelWidth(glyph.label, h * HOME_TEXT) + h * 0.4f)
        Shape.TRIGGER -> max(h * 0.8f, labelWidth(glyph.label, h * TRIGGER_TEXT) + h * 0.4f)
        Shape.MENU -> labelWidth(glyph.label, h * MENU_TEXT) + h * 0.5f
        Shape.BUMPER, Shape.KEY, Shape.UNBOUND -> max(h, labelWidth(glyph.label, h * CHIP_TEXT) + h * 0.7f)
    }

    private fun draw(canvas: Canvas, glyph: Glyph, style: ButtonStyle, left: Float, width: Float, cy: Float, h: Float) {
        val cx = left + width / 2
        box.set(left, cy - h / 2, left + width, cy + h / 2)
        when (glyph.shape) {
            Shape.FACE -> drawFace(canvas, glyph.label, style.faceColour(glyph.button!!), cx, cy, h)
            Shape.DPAD -> drawDpad(canvas, glyph.button!!, cx, cy, h)
            Shape.BUMPER -> drawChip(canvas, glyph.label, h / 2, h * CHIP_TEXT)
            Shape.KEY -> drawChip(canvas, glyph.label, h * 0.2f, h * CHIP_TEXT)
            Shape.MENU -> {
                box.inset(0f, h * (1 - MENU_HEIGHT) / 2)
                drawChip(canvas, glyph.label, box.height() / 2, h * MENU_TEXT)
            }
            Shape.TRIGGER -> drawTrigger(canvas, glyph.label, h)
            Shape.STICK -> drawStick(canvas, glyph.label, false, cx, cy, h)
            Shape.PRESSED_STICK -> drawStick(canvas, glyph.label, true, cx, cy, h)
            Shape.HOME -> drawHome(canvas, glyph, cx, cy, h)
            Shape.UNBOUND -> drawUnbound(canvas, h)
        }
    }

    private fun drawFace(canvas: Canvas, label: String, colour: Int, cx: Float, cy: Float, h: Float) {
        val stroke = h * 0.09f
        fill.color = BACKING
        canvas.drawCircle(cx, cy, h / 2, fill)
        line.color = colour
        line.strokeWidth = stroke
        canvas.drawCircle(cx, cy, (h - stroke) / 2, line)
        val s = h * 0.2f
        line.strokeWidth = h * 0.08f
        when (label) {
            "✕" -> {
                canvas.drawLine(cx - s, cy - s, cx + s, cy + s, line)
                canvas.drawLine(cx - s, cy + s, cx + s, cy - s, line)
            }
            "○" -> canvas.drawCircle(cx, cy, s * 1.1f, line)
            "□" -> canvas.drawRect(cx - s, cy - s, cx + s, cy + s, line)
            "△" -> {
                path.reset()
                path.moveTo(cx, cy - s * 1.15f)
                path.lineTo(cx + s * 1.2f, cy + s * 0.85f)
                path.lineTo(cx - s * 1.2f, cy + s * 0.85f)
                path.close()
                canvas.drawPath(path, line)
            }
            else -> drawLabel(canvas, label, cx, cy, h * FACE_TEXT, colour)
        }
    }

    private fun drawDpad(canvas: Canvas, direction: Button, cx: Float, cy: Float, h: Float) {
        val arm = h / 3
        val half = h / 2
        val corner = arm * 0.25f
        path.reset()
        path.addRoundRect(cx - arm / 2, cy - half, cx + arm / 2, cy + half, corner, corner, Path.Direction.CW)
        crossbar.reset()
        crossbar.addRoundRect(cx - half, cy - arm / 2, cx + half, cy + arm / 2, corner, corner, Path.Direction.CW)
        // union, not two contours: stroking both rects would draw their edges across the centre
        path.op(crossbar, Path.Op.UNION)
        fill.color = BACKING
        canvas.drawPath(path, fill)
        line.color = OUTLINE
        line.strokeWidth = dp(1.5f)
        canvas.drawPath(path, line)
        when (direction) {
            Button.UP -> box.set(cx - arm / 2, cy - half, cx + arm / 2, cy)
            Button.DOWN -> box.set(cx - arm / 2, cy, cx + arm / 2, cy + half)
            Button.LEFT -> box.set(cx - half, cy - arm / 2, cx, cy + arm / 2)
            else -> box.set(cx, cy - arm / 2, cx + half, cy + arm / 2)
        }
        fill.color = Palette.TEXT
        canvas.drawRoundRect(box, corner, corner, fill)
    }

    private fun drawChip(canvas: Canvas, label: String, corner: Float, textSize: Float) {
        fill.color = NEUTRAL
        canvas.drawRoundRect(box, corner, corner, fill)
        line.color = OUTLINE
        line.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(box, corner, corner, line)
        drawLabel(canvas, label, box.centerX(), box.centerY(), textSize, Color.WHITE)
    }

    private fun drawTrigger(canvas: Canvas, label: String, h: Float) {
        val dome = box.width() / 2
        val foot = h * 0.12f
        path.reset()
        path.addRoundRect(box, floatArrayOf(dome, h / 2, dome, h / 2, foot, foot, foot, foot), Path.Direction.CW)
        fill.color = NEUTRAL
        canvas.drawPath(path, fill)
        line.color = OUTLINE
        line.strokeWidth = dp(1.5f)
        canvas.drawPath(path, line)
        drawLabel(canvas, label, box.centerX(), box.centerY() + h * 0.12f, h * TRIGGER_TEXT, Color.WHITE)
    }

    private fun drawStick(canvas: Canvas, label: String, pressed: Boolean, cx: Float, cy: Float, h: Float) {
        fill.color = STICK_BASE
        canvas.drawCircle(cx, cy, h / 2, fill)
        line.strokeWidth = dp(1.5f)
        line.color = OUTLINE
        canvas.drawCircle(cx, cy, h / 2, line)
        if (pressed) {
            line.color = Palette.TEXT
            canvas.drawCircle(cx, cy, h * 0.42f, line)
        }
        val cap = h * if (pressed) 0.31f else 0.36f
        fill.color = STICK_CAP
        canvas.drawCircle(cx, cy, cap, fill)
        line.color = STICK_RIM
        canvas.drawCircle(cx, cy, cap, line)
        drawLabel(canvas, label, cx, cy, h * STICK_TEXT, Color.WHITE)
    }

    /** Nintendo's Home button keeps the binding name and is drawn as a house; the others are labelled. */
    private fun isHouse(glyph: Glyph): Boolean = glyph.label == Button.HOME.name

    private fun drawHome(canvas: Canvas, glyph: Glyph, cx: Float, cy: Float, h: Float) {
        val radius = h * 0.4f
        box.inset(0f, h * 0.1f)
        fill.color = NEUTRAL
        canvas.drawRoundRect(box, radius, radius, fill)
        line.color = OUTLINE
        line.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(box, radius, radius, line)
        if (!isHouse(glyph)) {
            drawLabel(canvas, glyph.label, cx, cy, h * HOME_TEXT, Palette.TEXT)
            return
        }
        path.reset()
        path.moveTo(cx, cy - h * 0.22f)
        path.lineTo(cx + h * 0.22f, cy - h * 0.02f)
        path.lineTo(cx + h * 0.15f, cy - h * 0.02f)
        path.lineTo(cx + h * 0.15f, cy + h * 0.19f)
        path.lineTo(cx - h * 0.15f, cy + h * 0.19f)
        path.lineTo(cx - h * 0.15f, cy - h * 0.02f)
        path.lineTo(cx - h * 0.22f, cy - h * 0.02f)
        path.close()
        fill.color = Palette.TEXT
        canvas.drawPath(path, fill)
    }

    private fun drawUnbound(canvas: Canvas, h: Float) {
        fill.color = BACKING
        canvas.drawRoundRect(box, h / 2, h / 2, fill)
        line.color = Palette.SELECTED
        line.strokeWidth = dp(2f)
        canvas.drawRoundRect(box, h / 2, h / 2, line)
        drawLabel(canvas, "?", box.centerX(), box.centerY(), h * CHIP_TEXT, Palette.SELECTED)
    }

    private fun drawLabel(canvas: Canvas, label: String, x: Float, y: Float, size: Float, colour: Int) {
        text.textSize = size
        text.color = colour
        val metrics = text.fontMetrics
        canvas.drawText(label, x, y - (metrics.ascent + metrics.descent) / 2, text)
    }

    private fun labelWidth(label: String, size: Float): Float {
        text.textSize = size
        return text.measureText(label)
    }

    private companion object {
        val GLYPH: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val NEUTRAL = Color.rgb(74, 90, 108)
        val BACKING = Color.argb(110, 0, 0, 0)
        val OUTLINE = Color.argb(140, 0, 0, 0)
        val STICK_BASE = Color.rgb(52, 58, 66)
        val STICK_CAP = Color.rgb(122, 131, 142)
        val STICK_RIM = Color.rgb(170, 178, 188)
        const val CHIP_TEXT = 0.62f
        const val FACE_TEXT = 0.55f
        const val TRIGGER_TEXT = 0.5f
        const val STICK_TEXT = 0.42f
        const val MENU_HEIGHT = 0.62f
        const val MENU_TEXT = 0.36f
        const val HOME_TEXT = 0.42f
    }
}
