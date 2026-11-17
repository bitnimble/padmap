package app.padmap.daemon;

import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import static app.padmap.daemon.Getevent.ABS_HAT0X;
import static app.padmap.daemon.Getevent.ABS_HAT0Y;
import static app.padmap.daemon.Getevent.ABS_X;
import static app.padmap.daemon.Getevent.ABS_Y;

import app.padmap.daemon.Getevent.Axes;
import app.padmap.daemon.Getevent.Device;
import app.padmap.daemon.Getevent.Range;
import app.padmap.layout.Button;
import app.padmap.layout.Mapper;
import app.padmap.layout.PadState;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the first evdev gamepad, reporting a PadState per input frame and rescanning when it goes away. The pad is
 * grabbed (hidden from Android) only while {@link #setGrab} asks for it.
 */
final class Gamepad implements Runnable {
    private static final int EV_SYN = 0, EV_KEY = 1, EV_ABS = 3, SYN_REPORT = 0, SYN_DROPPED = 3;
    private static final int EVENT_SIZE = android.os.Process.is64Bit() ? 24 : 16;
    private static final long RESCAN_MS = 2000;
    private static final int POLL_MS = 100;
    private static final Map<Integer, String> BUTTONS = Map.copyOf(Button.byCode());

    private final Consumer<PadState> listener;
    private final Consumer<String> connected;
    private volatile boolean wantGrab;

    /** connected receives the pad's name, or null when it goes away. */
    Gamepad(Consumer<PadState> listener, Consumer<String> connected) {
        this.listener = listener;
        this.connected = connected;
    }

    void setGrab(boolean grab) {
        wantGrab = grab;
    }

    @Override
    public void run() {
        while (true) {
            Device device = find();
            if (device == null) {
                SystemClock.sleep(RESCAN_MS);
                continue;
            }
            connected.accept(device.name());
            Log.i("gamepad: " + device.name() + " at " + device.path());
            try {
                read(device);
            } catch (IOException | ErrnoException e) {
                Log.i("gamepad " + device.path() + " lost: " + e.getMessage());
            } catch (RuntimeException e) {
                Log.e("reading the gamepad failed; reopening it", e);
            }
            connected.accept(null);
            listener.accept(PadState.NEUTRAL);
            SystemClock.sleep(RESCAN_MS);
        }
    }

    private Device find() {
        try {
            for (Device device : Getevent.parse(Platform.exec("getevent", "-p"))) {
                if (device.isGamepad()) return device;
            }
        } catch (IOException e) {
            Log.i("getevent -p failed: " + e.getMessage());
        }
        return null;
    }

    private void read(Device device) throws IOException, ErrnoException {
        Axes axes = Axes.of(device.ranges());
        Set<String> keys = new HashSet<>();
        Map<Integer, Integer> values = new HashMap<>();
        byte[] buffer = new byte[EVENT_SIZE * 64];
        ByteBuffer events = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        FileDescriptor fd = Os.open(device.path(), OsConstants.O_RDONLY, 0);
        boolean grabbed = false, grabFailed = false, waitReported = false, dropping = false;
        PadState last = PadState.NEUTRAL;
        try {
            while (true) {
                if (wantGrab && !grabbed && !atRest(last) && !waitReported) {
                    Log.i("gamepad: waiting for buttons and sticks to rest before taking it from Android");
                    waitReported = true;
                }
                // grabbing mid-press would leave Android holding that button or axis forever
                if (wantGrab && !grabbed && !grabFailed && atRest(last)) {
                    try {
                        Platform.grab(fd);
                        grabbed = true;
                    } catch (IOException e) {
                        Log.e("can't grab the gamepad; Android will see it too", e);
                        grabFailed = true;
                    }
                } else if (!wantGrab && grabbed) {
                    // EVIOCGRAB can't be released through libcore (it always passes a non-null pointer): reopen
                    FileDescriptor grabbedFd = fd;
                    fd = null;
                    Os.close(grabbedFd);
                    fd = Os.open(device.path(), OsConstants.O_RDONLY, 0);
                    grabbed = false;
                }
                StructPollfd poll = new StructPollfd();
                poll.fd = fd;
                poll.events = (short) OsConstants.POLLIN;
                if (Os.poll(new StructPollfd[] {poll}, POLL_MS) == 0) continue;
                if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLHUP)) != 0) throw new IOException("device gone");
                int length = Os.read(fd, buffer, 0, buffer.length);
                if (length <= 0) throw new IOException("end of stream");
                for (int offset = 0; offset + EVENT_SIZE <= length; offset += EVENT_SIZE) {
                    int type = events.getShort(offset + EVENT_SIZE - 8) & 0xffff;
                    int code = events.getShort(offset + EVENT_SIZE - 6) & 0xffff;
                    int value = events.getInt(offset + EVENT_SIZE - 4);
                    if (type == EV_SYN && code == SYN_DROPPED) {
                        dropping = true;
                    } else if (dropping) {
                        // after an overrun the frame is partial; resume at the next complete one
                        dropping = !(type == EV_SYN && code == SYN_REPORT);
                    } else if (type == EV_KEY) {
                        String button = BUTTONS.getOrDefault(code, String.format("KEY_%X", code));
                        if (value == 0) keys.remove(button);
                        else keys.add(button);
                    } else if (type == EV_ABS) {
                        values.put(code, value);
                    } else if (type == EV_SYN && code == SYN_REPORT) {
                        last = state(device, axes, keys, values);
                        listener.accept(last);
                    }
                }
            }
        } finally {
            if (fd != null) Os.close(fd);
        }
    }

    private static boolean atRest(PadState state) {
        return state.buttons().isEmpty() && state.left().magnitude() < Mapper.DEADZONE
                && state.right().magnitude() < Mapper.DEADZONE;
    }

    private static PadState state(Device device, Axes axes, Set<String> keys, Map<Integer, Integer> values) {
        Set<String> buttons = new HashSet<>(keys);
        if (fraction(device, values, axes.leftTrigger()) > 0.5) buttons.add("LT");
        if (fraction(device, values, axes.rightTrigger()) > 0.5) buttons.add("RT");
        double hatX = centred(device, values, ABS_HAT0X), hatY = centred(device, values, ABS_HAT0Y);
        if (hatX < -0.5) buttons.add("LEFT");
        if (hatX > 0.5) buttons.add("RIGHT");
        if (hatY < -0.5) buttons.add("UP");
        if (hatY > 0.5) buttons.add("DOWN");
        return new PadState(buttons,
                new PadState.Stick(centred(device, values, ABS_X), centred(device, values, ABS_Y)),
                new PadState.Stick(centred(device, values, axes.rightX()), centred(device, values, axes.rightY())));
    }

    private static double centred(Device device, Map<Integer, Integer> values, int code) {
        Range range = device.ranges().get(code);
        return range == null ? 0 : range.centred(values.getOrDefault(code, range.rest()));
    }

    private static double fraction(Device device, Map<Integer, Integer> values, int code) {
        Range range = device.ranges().get(code);
        return range == null ? 0 : range.fraction(values.getOrDefault(code, range.rest()));
    }
}
