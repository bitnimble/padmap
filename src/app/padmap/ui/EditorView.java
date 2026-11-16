package app.padmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.padmap.layout.Control;
import app.padmap.layout.Layout;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen layout editor drawn over the game: drag controls to place them, select one and press a pad button or
 * move a stick to bind it. Coordinates are normalized against the display size the daemon reports.
 */
final class EditorView extends FrameLayout {
    private static final double MIN_RADIUS = 0.02, MAX_RADIUS = 0.5, RESIZE_STEP = 1.15;

    interface Callbacks {
        void save(Layout layout);

        void close();
    }

    private final List<Control> controls;
    private final Callbacks callbacks;
    private int displayWidth, displayHeight;
    private final ControlsView controlsView;
    private final TextView hint;
    private int selected = -1;
    private boolean awaitingAimStick;

    EditorView(Context context, String pkg, Layout layout, int displayWidth, int displayHeight, Callbacks callbacks) {
        super(context);
        this.controls = new ArrayList<>(layout.controls());
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.callbacks = callbacks;
        setBackgroundColor(Color.argb(90, 0, 0, 0));
        controlsView = new ControlsView(context);
        addView(controlsView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout add = new LinearLayout(context);
        add.setGravity(Gravity.CENTER_HORIZONTAL);
        button(add, "+ Tap", v -> add(new Control.Tap("A", 0.5, 0.5, 0.06)));
        button(add, "+ Stick", v -> add(new Control.Joystick("LS", 0.5, 0.5, 0.12)));
        button(add, "+ Aim", v -> add(new Control.Aim("RB", "RS", 0.5, 0.5, 0.12)));
        button(add, "Save", v -> callbacks.save(new Layout(controls)));
        button(add, "Close", v -> callbacks.close());
        bar.addView(add);
        LinearLayout edit = new LinearLayout(context);
        edit.setGravity(Gravity.CENTER_HORIZONTAL);
        button(edit, "−", v -> resize(1 / RESIZE_STEP));
        button(edit, "+", v -> resize(RESIZE_STEP));
        button(edit, "Delete", v -> delete());
        bar.addView(edit);
        hint = new TextView(context);
        hint.setTextColor(Color.WHITE);
        hint.setShadowLayer(4, 0, 0, Color.BLACK);
        bar.addView(hint);
        addView(bar, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        updateHint(pkg);
    }

    /** The display rotated or resized while editing. */
    void setDisplaySize(int width, int height) {
        if (width == displayWidth && height == displayHeight) return;
        displayWidth = width;
        displayHeight = height;
        controlsView.invalidate();
    }

    /** A pad button the daemon saw pressed while editing. */
    void onButton(String button) {
        if (selected < 0) return;
        Control control = controls.get(selected);
        if (control instanceof Control.Tap tap) {
            controls.set(selected, new Control.Tap(button, tap.x(), tap.y(), tap.radius()));
        } else if (control instanceof Control.Aim aim) {
            controls.set(selected, new Control.Aim(button, aim.stick(), aim.x(), aim.y(), aim.radius()));
            awaitingAimStick = true;
        }
        changed();
    }

    /** A stick the daemon saw pushed while editing. */
    void onStick(String stick) {
        if (selected < 0) return;
        Control control = controls.get(selected);
        if (control instanceof Control.Joystick joystick) {
            controls.set(selected, new Control.Joystick(stick, joystick.x(), joystick.y(), joystick.radius()));
        } else if (control instanceof Control.Aim aim) {
            controls.set(selected, new Control.Aim(aim.button(), stick, aim.x(), aim.y(), aim.radius()));
            awaitingAimStick = false;
        }
        changed();
    }

    private void button(LinearLayout parent, String label, View.OnClickListener onClick) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(onClick);
        parent.addView(button);
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

    private void delete() {
        if (selected < 0) return;
        controls.remove(selected);
        select(-1);
    }

    private void select(int index) {
        selected = index;
        awaitingAimStick = false;
        changed();
    }

    private void changed() {
        updateHint(null);
        controlsView.invalidate();
    }

    private void updateHint(String pkg) {
        if (selected < 0) {
            hint.setText((pkg != null ? "Editing " + pkg + ". " : "")
                    + "Add a control, or tap one to change it. Select + Start closes the editor.");
            return;
        }
        Control control = controls.get(selected);
        if (control instanceof Control.Tap tap) {
            hint.setText("Tap on " + tap.button() + ". Press a pad button to change it. Drag to move, −/+ to resize.");
        } else if (control instanceof Control.Joystick joystick) {
            hint.setText("Joystick on " + joystick.stick()
                    + ". Move a stick to change it. Drag to move, −/+ sets how far it drags.");
        } else if (control instanceof Control.Aim aim) {
            hint.setText(awaitingAimStick
                    ? "Aim skill on " + aim.button() + ". Step 2 of 2: move the stick that aims it."
                    : "Aim skill on " + aim.button() + " + " + aim.stick()
                            + ". To change it, step 1 of 2: press its button. Drag to move, −/+ sets aim distance.");
        }
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
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] origin = new int[2];
        private float grabDx, grabDy;
        private boolean dragging;

        ControlsView(Context context) {
            super(context);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setShadowLayer(4, 0, 0, Color.BLACK);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            getLocationOnScreen(origin);
            text.setTextSize(displayHeight * 0.03f);
            for (int i = 0; i < controls.size(); i++) {
                Control control = controls.get(i);
                float x = screenX(control) - origin[0], y = screenY(control) - origin[1];
                float radius = (float) (control.radius() * displayHeight);
                fill.setColor(Color.argb(i == selected ? 110 : 70, 255, 255, 255));
                stroke.setColor(i == selected ? Color.YELLOW : Color.WHITE);
                if (control instanceof Control.Tap) {
                    canvas.drawCircle(x, y, radius, fill);
                } else {
                    canvas.drawCircle(x, y, Math.min(radius, displayHeight * 0.05f), fill);
                    stroke.setPathEffect(new android.graphics.DashPathEffect(new float[] {16, 12}, 0));
                }
                canvas.drawCircle(x, y, radius, stroke);
                stroke.setPathEffect(null);
                canvas.drawText(label(i), x, y + text.getTextSize() / 3, text);
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
