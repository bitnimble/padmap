package app.padmap.daemon;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import app.padmap.layout.Mapper.Contact;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays the mapper's desired contacts out as one multi-touch gesture stream on its own clock: each touch lands at
 * its anchor, holds for a couple of frames, then ramps to its target, so frame-sampling games (Unity) see where a
 * drag started; taps are held long enough to be seen.
 */
final class TouchInjector implements Runnable {
    private static final long TICK_MS = 8, LAND_MS = 32, RAMP_MS = 40, MIN_HOLD_MS = 50, ERROR_BACKOFF_MS = 1000;
    // a device id no real touchscreen has: with the touchscreen's own id, a finger's DOWN would silently merge
    // into our gesture instead of the dispatcher rejecting ours
    private static final int DEVICE_ID = -1;

    private static final class Touch {
        final int id;
        final long landedAt;
        final float anchorX, anchorY;
        float targetX, targetY, x, y;
        long movedAt;
        boolean wanted = true;

        Touch(int id, long landedAt, float anchorX, float anchorY) {
            this.id = id;
            this.landedAt = landedAt;
            this.anchorX = x = targetX = anchorX;
            this.anchorY = y = targetY = anchorY;
        }

        boolean ramped(long now) {
            return now - landedAt >= LAND_MS + RAMP_MS || (anchorX == targetX && anchorY == targetY);
        }
    }

    // held across building and injecting a batch, so a cancel can't land between a tick's events; taken before this
    private final Object injecting = new Object();
    private final Map<Integer, Touch> touches = new LinkedHashMap<>();
    private Map<Integer, Contact> target = Map.of();
    private int width, height;
    private long downTime, lastEventTime;

    /** Contacts are normalized; width and height are the display's logical size. */
    synchronized void setTarget(List<Contact> contacts, int width, int height) {
        Map<Integer, Contact> byControl = new HashMap<>();
        for (Contact contact : contacts) byControl.put(contact.control(), contact);
        target = byControl;
        this.width = width;
        this.height = height;
        notifyAll();
    }

    /** Abandons every touch without lifting it, so a held skill isn't cast. */
    void cancel() {
        synchronized (injecting) {
            MotionEvent event;
            synchronized (this) {
                target = Map.of();
                if (touches.isEmpty()) return;
                event = obtain(MotionEvent.ACTION_CANCEL);
                touches.clear();
            }
            try {
                inject(List.of(event));
            } catch (RuntimeException e) {
                Log.e("cancelling the gesture failed", e);
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                try {
                    while (target.isEmpty() && touches.isEmpty()) wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
            long pause = TICK_MS;
            synchronized (injecting) {
                List<MotionEvent> events;
                synchronized (this) {
                    events = tick(SystemClock.uptimeMillis());
                }
                try {
                    if (!inject(events)) dropGesture();
                } catch (RuntimeException e) {
                    Log.e("injection failed; dropping the gesture", e);
                    dropGesture();
                    pause = ERROR_BACKOFF_MS;
                }
            }
            SystemClock.sleep(pause);
        }
    }

    /** The dispatcher rejected us (a real finger took the screen) or injection broke: wait for the next input. */
    private synchronized void dropGesture() {
        touches.clear();
        target = Map.of();
    }

    private List<MotionEvent> tick(long now) {
        List<MotionEvent> events = new ArrayList<>();
        for (Map.Entry<Integer, Touch> entry : touches.entrySet()) {
            Contact contact = target.get(entry.getKey());
            Touch touch = entry.getValue();
            touch.wanted = contact != null;
            if (contact == null) continue;
            touch.targetX = (float) (contact.x() * width);
            touch.targetY = (float) (contact.y() * height);
        }
        for (Integer control : new ArrayList<>(touches.keySet())) {
            Touch touch = touches.get(control);
            // the last move goes out a frame before the lift, so an aimed skill is released where it was aimed
            if (!touch.wanted && touch.ramped(now) && now - touch.landedAt >= MIN_HOLD_MS
                    && now - touch.movedAt >= TICK_MS) {
                events.add(lift(control));
            }
        }
        for (Map.Entry<Integer, Contact> entry : target.entrySet()) {
            if (!touches.containsKey(entry.getKey())) events.add(land(entry.getKey(), entry.getValue(), now));
        }
        boolean moved = false;
        for (Touch touch : touches.values()) {
            float progress = Math.max(0, Math.min(1, (now - touch.landedAt - LAND_MS) / (float) RAMP_MS));
            float x = touch.anchorX + (touch.targetX - touch.anchorX) * progress;
            float y = touch.anchorY + (touch.targetY - touch.anchorY) * progress;
            if (x == touch.x && y == touch.y) continue;
            touch.x = x;
            touch.y = y;
            touch.movedAt = now;
            moved = true;
        }
        if (moved) events.add(obtain(MotionEvent.ACTION_MOVE));
        return events;
    }

    private MotionEvent land(int control, Contact contact, long now) {
        if (touches.isEmpty()) downTime = now;
        Touch touch = new Touch(freeId(), now, (float) (contact.anchorX() * width), (float) (contact.anchorY() * height));
        touch.targetX = (float) (contact.x() * width);
        touch.targetY = (float) (contact.y() * height);
        touches.put(control, touch);
        int index = touches.size() - 1;
        return obtain(index == 0
                ? MotionEvent.ACTION_DOWN
                : MotionEvent.ACTION_POINTER_DOWN | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    private MotionEvent lift(int control) {
        int index = new ArrayList<>(touches.keySet()).indexOf(control);
        MotionEvent event = obtain(touches.size() == 1
                ? MotionEvent.ACTION_UP
                : MotionEvent.ACTION_POINTER_UP | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        touches.remove(control);
        return event;
    }

    private int freeId() {
        for (int id = 0; ; id++) {
            boolean used = false;
            for (Touch touch : touches.values()) used |= touch.id == id;
            if (!used) return id;
        }
    }

    private MotionEvent obtain(int action) {
        int count = touches.size();
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        int i = 0;
        for (Touch touch : touches.values()) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = touch.id;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = touch.x;
            coords[i].y = touch.y;
            coords[i].pressure = 1;
            coords[i].size = 1;
            i++;
        }
        lastEventTime = Math.max(SystemClock.uptimeMillis(), lastEventTime + 1);
        return MotionEvent.obtain(downTime, lastEventTime, action, count, properties, coords,
                0, 0, 1, 1, DEVICE_ID, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    /** False as soon as the dispatcher rejects one; the rest are dropped. */
    private static boolean inject(List<MotionEvent> events) {
        boolean ok = true;
        for (MotionEvent event : events) {
            if (ok) ok = Platform.inject(event);
            event.recycle();
        }
        return ok;
    }
}
