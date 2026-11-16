package app.padmap.daemon;

import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import app.padmap.layout.PadState;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the first evdev gamepad, reporting a PadState per input frame and rescanning when it goes away. The pad is
 * grabbed (hidden from Android) only while {@link #setGrab} asks for it.
 */
final class Gamepad implements Runnable {
    private static final int EV_SYN = 0, EV_KEY = 1, EV_ABS = 3;
    private static final int BTN_SOUTH = 0x130;
    private static final int ABS_X = 0x00, ABS_Y = 0x01, ABS_Z = 0x02, ABS_RX = 0x03, ABS_RY = 0x04, ABS_RZ = 0x05,
            ABS_GAS = 0x09, ABS_BRAKE = 0x0a, ABS_HAT0X = 0x10, ABS_HAT0Y = 0x11;
    private static final int EVENT_SIZE = android.os.Process.is64Bit() ? 24 : 16;
    private static final long RESCAN_MS = 2000;
    private static final int POLL_MS = 100;
    private static final Map<Integer, String> BUTTONS = Map.ofEntries(
            Map.entry(0x130, "A"), Map.entry(0x131, "B"), Map.entry(0x133, "X"), Map.entry(0x134, "Y"),
            Map.entry(0x136, "LB"), Map.entry(0x137, "RB"), Map.entry(0x138, "LT"), Map.entry(0x139, "RT"),
            Map.entry(0x13a, "SELECT"), Map.entry(0x13b, "START"), Map.entry(0x13c, "HOME"),
            Map.entry(0x13d, "L3"), Map.entry(0x13e, "R3"),
            Map.entry(0x220, "UP"), Map.entry(0x221, "DOWN"), Map.entry(0x222, "LEFT"), Map.entry(0x223, "RIGHT"));
    private static final Pattern DEVICE = Pattern.compile("^add device \\d+: (\\S+)");
    private static final Pattern NAME = Pattern.compile("^\\s+name:\\s+\"(.*)\"");
    private static final Pattern SECTION = Pattern.compile("^\\s+([A-Z]+)\\s*\\(([0-9a-f]{4})\\):(.*)$");
    private static final Pattern ABS_RANGE =
            Pattern.compile("([0-9a-f]{4})\\s+: value -?\\d+, min (-?\\d+), max (-?\\d+)");

    record Range(int min, int max) {
        double centred(int value) {
            return Math.max(-1, Math.min(1, (value - (min + max) / 2.0) / ((max - min) / 2.0)));
        }

        double fraction(int value) {
            return Math.max(0, Math.min(1, (value - min) / (double) (max - min)));
        }
    }

    /** Where a pad reports each stick and trigger: xpad-style (RS on RX/RY) or HID-style (RS on Z/RZ). */
    record Axes(int rightX, int rightY, int leftTrigger, int rightTrigger) {
        static Axes of(Map<Integer, Range> ranges) {
            return ranges.containsKey(ABS_RX)
                    ? new Axes(ABS_RX, ABS_RY, ABS_Z, ABS_RZ)
                    : new Axes(ABS_Z, ABS_RZ, ABS_BRAKE, ABS_GAS);
        }
    }

    record Device(String path, String name, Set<Integer> keys, Map<Integer, Range> ranges) {
        boolean isGamepad() {
            return keys.contains(BTN_SOUTH) && ranges.containsKey(ABS_X) && ranges.containsKey(ABS_Y);
        }
    }

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
            }
            connected.accept(null);
            listener.accept(PadState.NEUTRAL);
            SystemClock.sleep(RESCAN_MS);
        }
    }

    private Device find() {
        try {
            for (Device device : parse(Platform.exec("getevent", "-p"))) {
                if (device.isGamepad()) return device;
            }
        } catch (IOException e) {
            Log.i("getevent -p failed: " + e.getMessage());
        }
        return null;
    }

    static List<Device> parse(String getevent) {
        List<Device> devices = new ArrayList<>();
        String path = null, name = "", section = "";
        Set<Integer> keys = new HashSet<>();
        Map<Integer, Range> ranges = new HashMap<>();
        for (String line : getevent.split("\n")) {
            Matcher device = DEVICE.matcher(line);
            if (device.find()) {
                if (path != null) devices.add(new Device(path, name, keys, ranges));
                path = device.group(1);
                name = "";
                section = "";
                keys = new HashSet<>();
                ranges = new HashMap<>();
                continue;
            }
            Matcher named = NAME.matcher(line);
            if (named.find()) {
                name = named.group(1);
                continue;
            }
            String body = line;
            Matcher header = SECTION.matcher(line);
            if (header.find()) {
                section = header.group(1);
                body = header.group(3);
            } else if (!line.startsWith("        ")) {
                section = "";
            }
            if (section.equals("KEY")) {
                for (String token : body.trim().split("\\s+")) {
                    if (token.matches("[0-9a-f]{4}")) keys.add(Integer.parseInt(token, 16));
                }
            } else if (section.equals("ABS")) {
                Matcher range = ABS_RANGE.matcher(body);
                if (range.find()) {
                    ranges.put(Integer.parseInt(range.group(1), 16),
                            new Range(Integer.parseInt(range.group(2)), Integer.parseInt(range.group(3))));
                }
            }
        }
        if (path != null) devices.add(new Device(path, name, keys, ranges));
        return devices;
    }

    private void read(Device device) throws IOException, ErrnoException {
        Axes axes = Axes.of(device.ranges());
        Set<String> keys = new HashSet<>();
        Map<Integer, Integer> values = new HashMap<>();
        byte[] buffer = new byte[EVENT_SIZE * 64];
        ByteBuffer events = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        FileDescriptor fd = Os.open(device.path(), OsConstants.O_RDONLY, 0);
        boolean grabbed = false;
        try {
            while (true) {
                if (wantGrab && !grabbed && keys.isEmpty()) {
                    Platform.grab(fd);
                    grabbed = true;
                } else if (!wantGrab && grabbed) {
                    // EVIOCGRAB can't be released through libcore (it always passes a non-null pointer): reopen
                    Os.close(fd);
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
                    if (type == EV_KEY) {
                        String button = BUTTONS.getOrDefault(code, String.format("KEY_%X", code));
                        if (value == 0) keys.remove(button);
                        else keys.add(button);
                    } else if (type == EV_ABS) {
                        values.put(code, value);
                    } else if (type == EV_SYN && code == 0) {
                        listener.accept(state(device, axes, keys, values));
                    }
                }
            }
        } finally {
            Os.close(fd);
        }
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
        Integer value = values.get(code);
        return range == null || value == null ? 0 : range.centred(value);
    }

    private static double fraction(Device device, Map<Integer, Integer> values, int code) {
        Range range = device.ranges().get(code);
        Integer value = values.get(code);
        return range == null || value == null ? 0 : range.fraction(value);
    }
}
