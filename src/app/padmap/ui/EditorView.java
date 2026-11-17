package app.padmap.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import app.padmap.R;
import app.padmap.layout.Button;
import app.padmap.layout.Chord;
import app.padmap.layout.Control;
import app.padmap.layout.Layout;
import app.padmap.layout.LayoutJson;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Full-screen layout editor drawn over the game: drag controls to place them, select one and press a pad button or
 * move a stick to bind it. Coordinates are normalized against the display size the daemon reports.
 */
final class EditorView extends FrameLayout {
    private static final double MIN_RADIUS = 0.02, MAX_RADIUS = 0.5, RESIZE_STEP = 1.15;
    private static final String PREF_STYLE = "buttonStyle";
    private static final int STYLE_GROUP = 1, EDITOR_CHORD_ITEM = 1000;
    private final Layout original;
    private final SharedPreferences prefs;
    private ButtonStyle style;
    private final List<List<Control>> sets = new ArrayList<>();
    private final List<String> chords = new ArrayList<>();
    private int current;
    private List<Control> controls;
    private int displayWidth, displayHeight;
    private final EditorTheme theme;
    private final ControlsView controlsView;
    private final LinearLayout editTools;
    private final TextView prompt, setMenu, chordLabel, setActions;
    private final View settingsMenu;
    private final CheckBox fallback;
    private final Consumer<String> saveEditorChord;
    private String editorChord;
    private final Set<String> gesture = new LinkedHashSet<>();
    private int selected = -1;
    private boolean awaitingAimStick, awaitingSetChord, awaitingEditorChord;

    EditorView(Context context, SharedPreferences prefs, Layout layout, int activeSet, String editorChord,
            int displayWidth, int displayHeight, Consumer<String> saveEditorChord, Runnable close) {
        super(context);
        this.prefs = prefs;
        this.editorChord = editorChord;
        this.saveEditorChord = saveEditorChord;
        style = ButtonStyle.valueOf(prefs.getString(PREF_STYLE, ButtonStyle.XBOX.name()));
        this.original = layout;
        for (Layout.MappingSet set : layout.sets()) {
            sets.add(new ArrayList<>(set.controls()));
            chords.add(set.chord());
        }
        current = activeSet >= 0 && activeSet < sets.size() ? activeSet : 0;
        controls = sets.get(current);
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        theme = new EditorTheme(context);
        setBackgroundColor(EditorTheme.SCRIM);
        controlsView = new ControlsView(context);
        addView(controlsView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        button(row, "+ Tap", EditorTheme.Tone.NORMAL, v -> add(new Control.Tap("A", 0.5, 0.5, 0.06)));
        button(row, "+ Stick", EditorTheme.Tone.NORMAL, v -> add(new Control.Joystick("LS", 0.5, 0.5, 0.12)));
        button(row, "+ Aim", EditorTheme.Tone.NORMAL, v -> add(new Control.Aim("RB", "RS", false, 0.5, 0.5, 0.12)));
        editTools = new LinearLayout(context);
        editTools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(theme.dp(1), theme.dp(32));
        rule.setMargins(theme.dp(8), 0, theme.dp(8), 0);
        editTools.addView(theme.divider(context), rule);
        button(editTools, "Smaller", EditorTheme.Tone.NORMAL, v -> resize(1 / RESIZE_STEP));
        button(editTools, "Larger", EditorTheme.Tone.NORMAL, v -> resize(RESIZE_STEP));
        button(editTools, "Delete", EditorTheme.Tone.DANGER, v -> delete());
        row.addView(editTools);
        prompt = label(context);
        prompt.setGravity(Gravity.END);
        LinearLayout.LayoutParams promptParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        promptParams.setMargins(theme.dp(8), 0, theme.dp(4), 0);
        row.addView(prompt, promptParams);
        settingsMenu = theme.iconButton(context, R.drawable.settings);
        settingsMenu.setContentDescription("Settings");
        settingsMenu.setOnClickListener(v -> chooseSetting());
        addButton(row, settingsMenu);
        button(row, "Done", EditorTheme.Tone.PRIMARY, v -> close.run());

        fallback = new CheckBox(context);
        fallback.setTextColor(EditorTheme.TEXT);
        fallback.setTextSize(18);
        fallback.setTypeface(EditorTheme.LABEL);
        fallback.setButtonTintList(new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {EditorTheme.SELECTED, EditorTheme.TEXT_DIM}));
        fallback.setOnClickListener(v -> setFallback(fallback.isChecked()));

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(theme.panel());
        panel.setElevation(theme.dp(8));
        int pad = theme.dp(12);
        panel.setPadding(pad, pad, pad, pad);
        LinearLayout setRow = new LinearLayout(context);
        setRow.setGravity(Gravity.CENTER_VERTICAL);
        setMenu = button(setRow, "", EditorTheme.Tone.NORMAL, v -> chooseSet());
        setRow.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1));
        chordLabel = label(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(theme.dp(8), 0, theme.dp(4), 0);
        setRow.addView(chordLabel, labelParams);
        button(setRow, "Set activation chord", EditorTheme.Tone.NORMAL, v -> awaitSetChord());
        setActions = button(setRow, "⋯", EditorTheme.Tone.NORMAL, v -> chooseSetAction());

        panel.addView(row, fullWidth(0));
        panel.addView(setRow, fullWidth(theme.dp(8)));
        panel.addView(fallback, fullWidth(theme.dp(8)));
        int width = Math.min(theme.dp(820), (int) (getResources().getDisplayMetrics().widthPixels * 0.92f));
        LayoutParams top = new LayoutParams(width, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        top.topMargin = theme.dp(12);
        addView(panel, top);
        updateTools();
    }

    /** The edited layout, or null if nothing changed. */
    Layout changedLayout() {
        List<Layout.MappingSet> edited = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) edited.add(new Layout.MappingSet(chords.get(i), sets.get(i)));
        Layout layout = new Layout(edited);
        // at the file's precision: touching a control without dragging it nudges its float position
        boolean same = LayoutJson.toJson(layout).toString().equals(LayoutJson.toJson(original).toString());
        return same ? null : layout;
    }

    void setDisplaySize(int width, int height) {
        if (width == displayWidth && height == displayHeight) return;
        displayWidth = width;
        displayHeight = height;
        controlsView.invalidate();
    }

    /** The buttons held this frame; the chord is bound when they are all released, so holding several binds a chord. */
    void onButtons(org.json.JSONArray held) {
        if (held == null) return;
        boolean bindsButton = selected >= 0
                && (controls.get(selected) instanceof Control.Tap || controls.get(selected) instanceof Control.Aim);
        if (!bindsButton && !awaitingSetChord && !awaitingEditorChord) return;
        List<String> editorButtons = Chord.parts(editorChord);
        for (int i = 0; i < held.length(); i++) {
            String button = held.optString(i);
            // the editor chord's buttons open and close the editor, so only the editor chord itself may use them
            if (awaitingEditorChord || !editorButtons.contains(button)) gesture.add(button);
        }
        if (held.length() > 0 || gesture.isEmpty()) return;
        String chord = Chord.of(gesture);
        gesture.clear();
        if (awaitingEditorChord) bindEditorChord(chord);
        else if (awaitingSetChord) bindSetChord(chord);
        else bind(chord);
    }

    void setEditorChord(String chord) {
        editorChord = chord;
    }

    private void bindEditorChord(String chord) {
        editorChord = chord;
        saveEditorChord.accept(chord);
        awaitingEditorChord = false;
        Toast.makeText(getContext(), "Editor chord: " + chordText(chord), Toast.LENGTH_SHORT).show();
        changed();
    }

    private void bindSetChord(String chord) {
        int other = chords.indexOf(chord);
        if (other >= 0 && other != current) {
            Toast.makeText(getContext(), chordText(chord) + " already activates set " + (other + 1),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        chords.set(current, chord);
        awaitingSetChord = false;
        changed();
    }

    private void bind(String button) {
        Control control = controls.get(selected);
        if (control instanceof Control.Tap tap) {
            controls.set(selected, new Control.Tap(button, tap.x(), tap.y(), tap.radius()));
        } else if (control instanceof Control.Aim aim) {
            controls.set(selected, new Control.Aim(button, aim.stick(), aim.fallback(), aim.x(), aim.y(), aim.radius()));
            awaitingAimStick = true;
        }
        changed();
    }

    void onStick(String stick) {
        if (selected < 0) return;
        Control control = controls.get(selected);
        if (control instanceof Control.Joystick joystick) {
            controls.set(selected, new Control.Joystick(stick, joystick.x(), joystick.y(), joystick.radius()));
        } else if (control instanceof Control.Aim aim) {
            controls.set(selected, new Control.Aim(aim.button(), stick, aim.fallback(), aim.x(), aim.y(), aim.radius()));
            awaitingAimStick = false;
        }
        changed();
    }

    private static TextView label(Context context) {
        TextView label = new TextView(context);
        label.setTextColor(EditorTheme.TEXT);
        label.setTextSize(18);
        label.setTypeface(EditorTheme.LABEL);
        return label;
    }

    private TextView button(LinearLayout parent, String label, EditorTheme.Tone tone, View.OnClickListener onClick) {
        TextView button = theme.button(getContext(), label, tone);
        button.setOnClickListener(onClick);
        addButton(parent, button);
        return button;
    }

    private void addButton(LinearLayout parent, View button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(theme.dp(4), 0, theme.dp(4), 0);
        parent.addView(button, params);
    }

    private void chooseSetting() {
        PopupMenu menu = new PopupMenu(getContext(), settingsMenu);
        SubMenu styles = menu.getMenu().addSubMenu("Button style: " + style.title);
        for (ButtonStyle option : ButtonStyle.values()) styles.add(STYLE_GROUP, option.ordinal(), 0, option.title);
        menu.getMenu().add(0, EDITOR_CHORD_ITEM, 0, withGlyphs("Editor chord  ", editorChord, 36));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getGroupId() == STYLE_GROUP) {
                style = ButtonStyle.values()[item.getItemId()];
                prefs.edit().putString(PREF_STYLE, style.name()).apply();
                changed();
            } else if (item.getItemId() == EDITOR_CHORD_ITEM) {
                select(-1);
                awaitingEditorChord = true;
                changed();
            }
            return true;
        });
        menu.show();
    }

    private void chooseSet() {
        PopupMenu menu = new PopupMenu(getContext(), setMenu);
        for (int i = 0; i < sets.size(); i++) {
            menu.getMenu().add(0, i, 0, setName(i));
        }
        menu.setOnMenuItemClickListener(item -> {
            showSet(item.getItemId());
            return true;
        });
        menu.show();
    }

    private void chooseSetAction() {
        PopupMenu menu = new PopupMenu(getContext(), setActions);
        menu.getMenu().add(0, 0, 0, "Duplicate");
        if (sets.size() > 1) menu.getMenu().add(0, 1, 0, "Delete set");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) duplicateSet();
            else deleteSet();
            return true;
        });
        menu.show();
    }

    private void duplicateSet() {
        sets.add(new ArrayList<>(controls));
        chords.add(null);
        showSet(sets.size() - 1);
        awaitSetChord();
    }

    private void deleteSet() {
        if (sets.size() <= 1) return;
        sets.remove(current);
        chords.remove(current);
        showSet(Math.min(current, sets.size() - 1));
    }

    private static String setName(int index) {
        return "Set " + (index + 1);
    }

    private void showSet(int index) {
        current = index;
        controls = sets.get(index);
        select(-1);
    }

    private void awaitSetChord() {
        select(-1);
        awaitingSetChord = true;
        changed();
    }

    private CharSequence withGlyphs(String text, String chord, int heightDp) {
        SpannableStringBuilder label = new SpannableStringBuilder(text);
        List<String> parts = Chord.parts(chord);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) label.append(" + ");
            Drawable glyph = theme.bindingDrawable(getResources(), List.of(parts.get(i)), style, theme.dp(heightDp));
            label.append('￼');
            label.setSpan(new CentredSpan(glyph), label.length() - 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return label;
    }

    /** A drawable centred on the text's own middle, which ImageSpan.ALIGN_CENTER misses by the descent. */
    private static final class CentredSpan extends ReplacementSpan {
        private final Drawable drawable;

        CentredSpan(Drawable drawable) {
            this.drawable = drawable;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            Rect bounds = drawable.getBounds();
            if (metrics != null) {
                int middle = (paint.getFontMetricsInt().ascent + paint.getFontMetricsInt().descent) / 2;
                metrics.ascent = metrics.top = Math.min(paint.getFontMetricsInt().ascent, middle - bounds.height() / 2);
                metrics.descent = metrics.bottom = Math.max(paint.getFontMetricsInt().descent, middle + bounds.height() / 2);
            }
            return bounds.width();
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                Paint paint) {
            canvas.save();
            canvas.translate(x, y + (paint.ascent() + paint.descent()) / 2 - drawable.getBounds().height() / 2f);
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    private String chordText(String chord) {
        List<String> labels = new ArrayList<>();
        for (String part : Chord.parts(chord)) {
            Button button = Button.named(part);
            labels.add(button == null ? part : style.label(button));
        }
        return String.join("+", labels);
    }

    private static LinearLayout.LayoutParams fullWidth(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private void add(Control control) {
        controls.add(control);
        select(controls.size() - 1);
    }

    private void resize(double factor) {
        if (selected < 0) return;
        Control control = controls.get(selected);
        controls.set(selected, control.resized(Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, control.radius() * factor))));
        changed();
    }

    private void setFallback(boolean on) {
        if (selected < 0 || !(controls.get(selected) instanceof Control.Aim aim)) return;
        controls.set(selected, new Control.Aim(aim.button(), aim.stick(), on, aim.x(), aim.y(), aim.radius()));
        changed();
    }

    private void delete() {
        if (selected < 0) return;
        controls.remove(selected);
        select(-1);
    }

    private void select(int index) {
        selected = index;
        awaitingAimStick = false;
        awaitingSetChord = false;
        awaitingEditorChord = false;
        gesture.clear();
        changed();
    }

    private void changed() {
        updateTools();
        controlsView.invalidate();
    }

    private void updateTools() {
        prompt.setText(awaitingEditorChord ? "Press new editor chord buttons now" : "");
        setMenu.setText(setName(current) + " ▾");
        String chord = chords.get(current);
        chordLabel.setText(awaitingSetChord ? "Press activation chord buttons now"
                : chord == null ? "" : withGlyphs("", chord, 30));
        editTools.setVisibility(selected >= 0 ? VISIBLE : GONE);
        Control.Aim selectedAim = selected >= 0 && controls.get(selected) instanceof Control.Aim aim ? aim : null;
        fallback.setVisibility(selectedAim != null ? VISIBLE : GONE);
        if (selectedAim == null) return;
        String other = selectedAim.stick().equals("LS") ? "RS" : "LS";
        fallback.setText("Aim with " + other + " when " + selectedAim.stick() + " is idle");
        fallback.setChecked(selectedAim.fallback());
    }

    private String label(int index) {
        Control control = controls.get(index);
        if (control instanceof Control.Tap tap) return tap.button();
        if (control instanceof Control.Joystick joystick) return joystick.stick();
        if (control instanceof Control.Aim aim) {
            return aim.button() + "+" + (awaitingAimStick && index == selected ? "?" : aim.stick());
        }
        return "?";
    }

    private final class ControlsView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final DashPathEffect dash = new DashPathEffect(new float[] {theme.dp(10), theme.dp(8)}, 0);
        private final int[] origin = new int[2];
        private float grabDx, grabDy;
        private boolean dragging;

        ControlsView(Context context) {
            super(context);
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            getLocationOnScreen(origin);
            float glyph = Math.max(theme.dp(24), displayHeight * 0.042f);
            for (int i = 0; i < controls.size(); i++) {
                Control control = controls.get(i);
                boolean on = i == selected;
                float x = screenX(control) - origin[0], y = screenY(control) - origin[1];
                float radius = (float) (control.radius() * displayHeight);
                stroke.setColor(on ? EditorTheme.SELECTED : Color.argb(210, 255, 255, 255));
                stroke.setStrokeWidth(theme.dp(on ? 4 : 2.5f));
                if (control instanceof Control.Tap) {
                    fill.setColor(on ? Color.argb(70, 255, 182, 39) : Color.argb(45, 255, 255, 255));
                    canvas.drawCircle(x, y, radius, fill);
                    canvas.drawCircle(x, y, radius, stroke);
                } else {
                    stroke.setPathEffect(dash);
                    canvas.drawCircle(x, y, radius, stroke);
                    stroke.setPathEffect(null);
                    float knob = Math.min(radius, displayHeight * 0.05f);
                    fill.setColor(EditorTheme.KNOB);
                    canvas.drawCircle(x, y, knob, fill);
                    canvas.drawCircle(x, y, knob, stroke);
                }
                theme.drawBinding(canvas, Chord.parts(label(i)), style, x, y, glyph);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getRawX(), y = event.getRawY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    int hit = hit(x, y);
                    select(hit);
                    dragging = hit >= 0;
                    if (dragging) {
                        grabDx = screenX(controls.get(hit)) - x;
                        grabDy = screenY(controls.get(hit)) - y;
                    }
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (!dragging || selected < 0) break;
                    double nx = clamp((x + grabDx) / displayWidth), ny = clamp((y + grabDy) / displayHeight);
                    controls.set(selected, controls.get(selected).moved(nx, ny));
                    invalidate();
                }
                default -> dragging = false;
            }
            return true;
        }

        private int hit(float x, float y) {
            for (int i = controls.size() - 1; i >= 0; i--) {
                Control control = controls.get(i);
                float reach = Math.max((float) (control.radius() * displayHeight), displayHeight * 0.05f);
                if (Math.hypot(screenX(control) - x, screenY(control) - y) <= reach) return i;
            }
            return -1;
        }

        private float screenX(Control control) {
            return (float) (control.x() * displayWidth);
        }

        private float screenY(Control control) {
            return (float) (control.y() * displayHeight);
        }

        private double clamp(double value) {
            return Math.max(0, Math.min(1, value));
        }
    }
}
