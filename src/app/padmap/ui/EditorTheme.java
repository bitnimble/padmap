package app.padmap.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import app.padmap.layout.Button;
import java.util.ArrayList;
import java.util.List;

/** The editor overlay's palette, toolbar widgets, and the controller glyphs drawn for each binding. */
final class EditorTheme {
    static final int SCRIM = Color.argb(110, 0, 0, 0);
    static final int TEXT = Color.rgb(232, 238, 245);
    static final int TEXT_DIM = Color.rgb(154, 168, 184);
    static final int SELECTED = Color.rgb(255, 182, 39);
    static final int KNOB = Color.argb(200, 43, 58, 76);
    static final Typeface LABEL = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final int SURFACE = Color.rgb(43, 58, 76);
    private static final int PANEL = Color.argb(235, 22, 30, 42);
    private static final int EDGE = Color.argb(60, 255, 255, 255);
    private static final int PRIMARY = Color.rgb(26, 159, 255);
    private static final int DANGER = Color.rgb(229, 72, 77);
    private static final int NEUTRAL = Color.rgb(74, 90, 108);
    private static final int BACKING = Color.argb(110, 0, 0, 0);
    private static final int OUTLINE = Color.argb(140, 0, 0, 0);
    private static final int STICK_BASE = Color.rgb(52, 58, 66);
    private static final int STICK_CAP = Color.rgb(122, 131, 142);
    private static final int STICK_RIM = Color.rgb(170, 178, 188);
    private static final Typeface GLYPH = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final float CHIP_TEXT = 0.62f, FACE_TEXT = 0.55f, TRIGGER_TEXT = 0.5f, STICK_TEXT = 0.42f;
    private static final float MENU_HEIGHT = 0.62f, MENU_TEXT = 0.36f;

    enum Tone { NORMAL, PRIMARY, DANGER }

    private enum Shape { FACE, DPAD, BUMPER, TRIGGER, STICK, PRESSED_STICK, MENU, HOME, KEY, UNBOUND }

    private record Glyph(Shape shape, Button button, String label) {}

    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path path = new Path();
    private final Path crossbar = new Path();

    EditorTheme(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(GLYPH);
        text.setTextAlign(Paint.Align.CENTER);
        text.setShadowLayer(dp(2), 0, 0, Color.BLACK);
    }

    int dp(float value) {
        return Math.round(value * density);
    }

    TextView button(Context context, String label, Tone tone) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(20);
        view.setTypeface(LABEL);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(tone == Tone.PRIMARY ? Color.WHITE : tone == Tone.DANGER ? DANGER : TEXT);
        view.setMinWidth(dp(48));
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(10));
        fill.setColor(tone == Tone.PRIMARY ? PRIMARY : SURFACE);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), fill, null));
        view.setClickable(true);
        return view;
    }

    GradientDrawable panel() {
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(PANEL);
        panel.setCornerRadius(dp(16));
        panel.setStroke(dp(1), EDGE);
        return panel;
    }

    View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(EDGE);
        return divider;
    }

    /** Draws a binding centred on (cx, cy) as controller glyphs, one per part, joined by "+". */
    void drawBinding(Canvas canvas, List<String> parts, ButtonStyle style, float cx, float cy, float height) {
        List<Glyph> glyphs = new ArrayList<>();
        for (String part : parts) glyphs.add(glyph(part, style));
        float gap = height * 0.18f, plus = labelWidth("+", height * CHIP_TEXT);
        float[] widths = new float[glyphs.size()];
        float total = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            widths[i] = width(glyphs.get(i), height);
            total += widths[i] + (i > 0 ? plus + 2 * gap : 0);
        }
        float x = cx - total / 2;
        for (int i = 0; i < glyphs.size(); i++) {
            if (i > 0) {
                drawLabel(canvas, "+", x + gap + plus / 2, cy, height * CHIP_TEXT, TEXT);
                x += plus + 2 * gap;
            }
            draw(canvas, glyphs.get(i), style, x, widths[i], cy, height);
            x += widths[i];
        }
    }

    private static Glyph glyph(String part, ButtonStyle style) {
        Button button = Button.named(part);
        if (button != null) return new Glyph(shape(button), button, style.label(button));
        if (part.equals("?")) return new Glyph(Shape.UNBOUND, null, part);
        boolean stick = part.equals("LS") || part.equals("RS");
        return new Glyph(stick ? Shape.STICK : Shape.KEY, null, part);
    }

    private static Shape shape(Button button) {
        return switch (button) {
            case A, B, X, Y -> Shape.FACE;
            case UP, DOWN, LEFT, RIGHT -> Shape.DPAD;
            case LB, RB -> Shape.BUMPER;
            case LT, RT -> Shape.TRIGGER;
            case L3, R3 -> Shape.PRESSED_STICK;
            case SELECT, START -> Shape.MENU;
            case HOME -> Shape.HOME;
        };
    }

    private float width(Glyph glyph, float h) {
        return switch (glyph.shape()) {
            case FACE, DPAD, STICK, PRESSED_STICK -> h;
            case HOME -> h * 0.8f;
            case TRIGGER -> Math.max(h * 0.8f, labelWidth(glyph.label(), h * TRIGGER_TEXT) + h * 0.4f);
            case MENU -> labelWidth(glyph.label(), h * MENU_TEXT) + h * 0.5f;
            case BUMPER, KEY, UNBOUND -> Math.max(h, labelWidth(glyph.label(), h * CHIP_TEXT) + h * 0.7f);
        };
    }

    private void draw(Canvas canvas, Glyph glyph, ButtonStyle style, float left, float width, float cy, float h) {
        float cx = left + width / 2;
        box.set(left, cy - h / 2, left + width, cy + h / 2);
        switch (glyph.shape()) {
            case FACE -> drawFace(canvas, glyph.label(), style.faceColour(glyph.button()), cx, cy, h);
            case DPAD -> drawDpad(canvas, glyph.button(), cx, cy, h);
            case BUMPER -> drawChip(canvas, glyph.label(), h / 2, h * CHIP_TEXT);
            case KEY -> drawChip(canvas, glyph.label(), h * 0.2f, h * CHIP_TEXT);
            case MENU -> {
                box.inset(0, h * (1 - MENU_HEIGHT) / 2);
                drawChip(canvas, glyph.label(), box.height() / 2, h * MENU_TEXT);
            }
            case TRIGGER -> drawTrigger(canvas, glyph.label(), h);
            case STICK -> drawStick(canvas, glyph.label(), false, cx, cy, h);
            case PRESSED_STICK -> drawStick(canvas, glyph.label(), true, cx, cy, h);
            case HOME -> drawHome(canvas, cx, cy, h);
            case UNBOUND -> drawUnbound(canvas, h);
        }
    }

    private void drawFace(Canvas canvas, String label, int colour, float cx, float cy, float h) {
        float stroke = h * 0.09f;
        fill.setColor(BACKING);
        canvas.drawCircle(cx, cy, h / 2, fill);
        line.setColor(colour);
        line.setStrokeWidth(stroke);
        canvas.drawCircle(cx, cy, (h - stroke) / 2, line);
        float s = h * 0.2f;
        line.setStrokeWidth(h * 0.08f);
        switch (label) {
            case "✕" -> {
                canvas.drawLine(cx - s, cy - s, cx + s, cy + s, line);
                canvas.drawLine(cx - s, cy + s, cx + s, cy - s, line);
            }
            case "○" -> canvas.drawCircle(cx, cy, s * 1.1f, line);
            case "□" -> canvas.drawRect(cx - s, cy - s, cx + s, cy + s, line);
            case "△" -> {
                path.reset();
                path.moveTo(cx, cy - s * 1.15f);
                path.lineTo(cx + s * 1.2f, cy + s * 0.85f);
                path.lineTo(cx - s * 1.2f, cy + s * 0.85f);
                path.close();
                canvas.drawPath(path, line);
            }
            default -> drawLabel(canvas, label, cx, cy, h * FACE_TEXT, colour);
        }
    }

    private void drawDpad(Canvas canvas, Button direction, float cx, float cy, float h) {
        float arm = h / 3, half = h / 2, corner = arm * 0.25f;
        path.reset();
        path.addRoundRect(cx - arm / 2, cy - half, cx + arm / 2, cy + half, corner, corner, Path.Direction.CW);
        crossbar.reset();
        crossbar.addRoundRect(cx - half, cy - arm / 2, cx + half, cy + arm / 2, corner, corner, Path.Direction.CW);
        // union, not two contours: stroking both rects would draw their edges across the centre
        path.op(crossbar, Path.Op.UNION);
        fill.setColor(BACKING);
        canvas.drawPath(path, fill);
        line.setColor(OUTLINE);
        line.setStrokeWidth(dp(1.5f));
        canvas.drawPath(path, line);
        switch (direction) {
            case UP -> box.set(cx - arm / 2, cy - half, cx + arm / 2, cy);
            case DOWN -> box.set(cx - arm / 2, cy, cx + arm / 2, cy + half);
            case LEFT -> box.set(cx - half, cy - arm / 2, cx, cy + arm / 2);
            case RIGHT -> box.set(cx, cy - arm / 2, cx + half, cy + arm / 2);
        }
        fill.setColor(TEXT);
        canvas.drawRoundRect(box, corner, corner, fill);
    }

    private void drawChip(Canvas canvas, String label, float corner, float textSize) {
        fill.setColor(NEUTRAL);
        canvas.drawRoundRect(box, corner, corner, fill);
        line.setColor(OUTLINE);
        line.setStrokeWidth(dp(1.5f));
        canvas.drawRoundRect(box, corner, corner, line);
        drawLabel(canvas, label, box.centerX(), box.centerY(), textSize, Color.WHITE);
    }

    private void drawTrigger(Canvas canvas, String label, float h) {
        float dome = box.width() / 2, foot = h * 0.12f;
        path.reset();
        path.addRoundRect(box, new float[] {dome, h / 2, dome, h / 2, foot, foot, foot, foot}, Path.Direction.CW);
        fill.setColor(NEUTRAL);
        canvas.drawPath(path, fill);
        line.setColor(OUTLINE);
        line.setStrokeWidth(dp(1.5f));
        canvas.drawPath(path, line);
        drawLabel(canvas, label, box.centerX(), box.centerY() + h * 0.12f, h * TRIGGER_TEXT, Color.WHITE);
    }

    private void drawStick(Canvas canvas, String label, boolean pressed, float cx, float cy, float h) {
        fill.setColor(STICK_BASE);
        canvas.drawCircle(cx, cy, h / 2, fill);
        line.setStrokeWidth(dp(1.5f));
        line.setColor(OUTLINE);
        canvas.drawCircle(cx, cy, h / 2, line);
        if (pressed) {
            line.setColor(TEXT);
            canvas.drawCircle(cx, cy, h * 0.42f, line);
        }
        float cap = h * (pressed ? 0.31f : 0.36f);
        fill.setColor(STICK_CAP);
        canvas.drawCircle(cx, cy, cap, fill);
        line.setColor(STICK_RIM);
        canvas.drawCircle(cx, cy, cap, line);
        drawLabel(canvas, label, cx, cy, h * STICK_TEXT, Color.WHITE);
    }

    private void drawHome(Canvas canvas, float cx, float cy, float h) {
        fill.setColor(NEUTRAL);
        canvas.drawCircle(cx, cy, h * 0.4f, fill);
        line.setColor(OUTLINE);
        line.setStrokeWidth(dp(1.5f));
        canvas.drawCircle(cx, cy, h * 0.4f, line);
        path.reset();
        path.moveTo(cx, cy - h * 0.22f);
        path.lineTo(cx + h * 0.22f, cy - h * 0.02f);
        path.lineTo(cx + h * 0.15f, cy - h * 0.02f);
        path.lineTo(cx + h * 0.15f, cy + h * 0.19f);
        path.lineTo(cx - h * 0.15f, cy + h * 0.19f);
        path.lineTo(cx - h * 0.15f, cy - h * 0.02f);
        path.lineTo(cx - h * 0.22f, cy - h * 0.02f);
        path.close();
        fill.setColor(TEXT);
        canvas.drawPath(path, fill);
    }

    private void drawUnbound(Canvas canvas, float h) {
        fill.setColor(BACKING);
        canvas.drawRoundRect(box, h / 2, h / 2, fill);
        line.setColor(SELECTED);
        line.setStrokeWidth(dp(2));
        canvas.drawRoundRect(box, h / 2, h / 2, line);
        drawLabel(canvas, "?", box.centerX(), box.centerY(), h * CHIP_TEXT, SELECTED);
    }

    private void drawLabel(Canvas canvas, String label, float x, float y, float size, int colour) {
        text.setTextSize(size);
        text.setColor(colour);
        Paint.FontMetrics metrics = text.getFontMetrics();
        canvas.drawText(label, x, y - (metrics.ascent + metrics.descent) / 2, text);
    }

    private float labelWidth(String label, float size) {
        text.setTextSize(size);
        return text.measureText(label);
    }
}
