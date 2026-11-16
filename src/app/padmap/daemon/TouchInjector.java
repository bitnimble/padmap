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
    private static final long TICK_MS = 8, LAND_MS = 32, RAMP_MS = 40, MIN_HOLD_MS = 50;

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
    }

    private final Map<Integer, Touch> touches = new LinkedHashMap<>();
    private final int deviceId = Platform.touchscreenDeviceId();
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
    synchronized void cancel() {
        target = Map.of();
        if (touches.isEmpty()) return;
        send(MotionEvent.ACTION_CANCEL);
        touches.clear();
    }

    @Override
    public void run() {
        synchronized (this) {
            while (true) {
                try {
                    while (target.isEmpty() && touches.isEmpty()) wait();
                    tick(SystemClock.uptimeMillis());
                    wait(TICK_MS);
                } catch (InterruptedException e) {
                    return;
                } catch (RuntimeException e) {
                    Log.e("injection failed; dropping the gesture", e);
                    touches.clear();
                }
            }
        }
    }

    private void tick(long now) {
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
            if (!touch.wanted && now - touch.landedAt >= MIN_HOLD_MS && now - touch.movedAt >= TICK_MS) {
                if (!lift(control)) return;
            }
        }
        for (Map.Entry<Integer, Contact> entry : target.entrySet()) {
            if (touches.containsKey(entry.getKey())) continue;
            Contact contact = entry.getValue();
            if (!land(entry.getKey(), now, (float) (contact.anchorX() * width), (float) (contact.anchorY() * height),
                    (float) (contact.x() * width), (float) (contact.y() * height))) return;
        }
        boolean moved = false;
        for (Touch touch : touches.values()) {
            if (!touch.wanted) continue;
            float progress = Math.max(0, Math.min(1, (now - touch.landedAt - LAND_MS) / (float) RAMP_MS));
            float x = touch.anchorX + (touch.targetX - touch.anchorX) * progress;
            float y = touch.anchorY + (touch.targetY - touch.anchorY) * progress;
            if (x == touch.x && y == touch.y) continue;
            touch.x = x;
            touch.y = y;
            touch.movedAt = now;
            moved = true;
        }
        if (moved && !send(MotionEvent.ACTION_MOVE)) touches.clear();
    }

    private boolean land(int control, long now, float anchorX, float anchorY, float targetX, float targetY) {
        if (touches.isEmpty()) downTime = now;
        Touch touch = new Touch(freeId(), now, anchorX, anchorY);
        touch.targetX = targetX;
        touch.targetY = targetY;
        touches.put(control, touch);
        int index = touches.size() - 1;
        boolean sent = send(index == 0
                ? MotionEvent.ACTION_DOWN
                : MotionEvent.ACTION_POINTER_DOWN | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        if (!sent) touches.clear();
        return sent;
    }

    private boolean lift(int control) {
        int index = new ArrayList<>(touches.keySet()).indexOf(control);
        boolean sent = send(touches.size() == 1
                ? MotionEvent.ACTION_UP
                : MotionEvent.ACTION_POINTER_UP | index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        if (sent) touches.remove(control);
        else touches.clear();
        return sent;
    }

    private int freeId() {
        for (int id = 0; ; id++) {
            boolean used = false;
            for (Touch touch : touches.values()) used |= touch.id == id;
            if (!used) return id;
        }
    }

    /** False when the dispatcher dropped it: a real finger took over the screen and ended our gesture. */
    private boolean send(int action) {
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
        MotionEvent event = MotionEvent.obtain(downTime, lastEventTime, action, count, properties, coords,
                0, 0, 1, 1, deviceId, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            return Platform.inject(event);
        } finally {
            event.recycle();
        }
    }
}
