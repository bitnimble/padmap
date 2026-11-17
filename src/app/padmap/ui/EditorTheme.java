package app.padmap.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
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
    private static final Typeface GLYPH = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    enum Tone { NORMAL, PRIMARY, DANGER }

    private final float density;
    private final Paint chipFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chip = new RectF();

    EditorTheme(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        chipText.setTypeface(GLYPH);
        chipText.setTextAlign(Paint.Align.CENTER);
        chipText.setShadowLayer(dp(2), 0, 0, Color.BLACK);
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

    /**
     * Draws a binding centred on (cx, cy) as controller glyphs, one per part, joined by "+": face buttons as
     * their coloured circles, everything else (shoulders, triggers, d-pad, sticks) as neutral pills.
     */
    void drawBinding(Canvas canvas, List<String> parts, float cx, float cy, float height) {
        chipText.setTextSize(height * 0.62f);
        float gap = height * 0.18f, plus = chipText.measureText("+");
        float[] widths = new float[parts.size()];
        float total = 0;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            widths[i] = isFace(part) ? height : Math.max(height, chipText.measureText(part) + height * 0.7f);
            total += widths[i] + (i > 0 ? plus + 2 * gap : 0);
        }
        Paint.FontMetrics metrics = chipText.getFontMetrics();
        float baseline = cy - (metrics.ascent + metrics.descent) / 2;
        float x = cx - total / 2;
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                chipText.setColor(TEXT);
                canvas.drawText("+", x + gap + plus / 2, baseline, chipText);
                x += plus + 2 * gap;
            }
            String part = parts.get(i);
            boolean unbound = part.equals("?");
            chip.set(x, cy - height / 2, x + widths[i], cy + height / 2);
            chipFill.setStyle(Paint.Style.FILL);
            chipFill.setColor(unbound ? Color.argb(110, 0, 0, 0) : colour(part));
            canvas.drawRoundRect(chip, height / 2, height / 2, chipFill);
            chipFill.setStyle(Paint.Style.STROKE);
            chipFill.setStrokeWidth(dp(unbound ? 2 : 1.5f));
            chipFill.setColor(unbound ? SELECTED : Color.argb(140, 0, 0, 0));
            canvas.drawRoundRect(chip, height / 2, height / 2, chipFill);
            chipText.setColor(unbound ? SELECTED : part.equals("Y") ? Color.rgb(40, 32, 8) : Color.WHITE);
            canvas.drawText(part, chip.centerX(), baseline, chipText);
            x += widths[i];
        }
    }

    private static boolean isFace(String part) {
        return part.length() == 1 && "ABXY".contains(part);
    }

    private static int colour(String part) {
        return switch (part) {
            case "A" -> Color.rgb(63, 185, 80);
            case "B" -> DANGER;
            case "X" -> Color.rgb(59, 130, 246);
            case "Y" -> Color.rgb(242, 201, 76);
            default -> NEUTRAL;
        };
    }
}
