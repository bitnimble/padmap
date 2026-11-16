package app.padmap.daemon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Input devices as `getevent -p` describes them: key codes, and per axis its range and resting value. */
final class Getevent {
    static final int BTN_SOUTH = 0x130;
    static final int ABS_X = 0x00, ABS_Y = 0x01, ABS_Z = 0x02, ABS_RX = 0x03, ABS_RY = 0x04, ABS_RZ = 0x05,
            ABS_GAS = 0x09, ABS_BRAKE = 0x0a, ABS_HAT0X = 0x10, ABS_HAT0Y = 0x11;

    private static final Pattern DEVICE = Pattern.compile("^add device \\d+: (\\S+)");
    private static final Pattern NAME = Pattern.compile("^\\s+name:\\s+\"(.*)\"");
    private static final Pattern SECTION = Pattern.compile("^\\s+([A-Z]+)\\s*\\(([0-9a-f]{4})\\):(.*)$");
    private static final Pattern ABS_RANGE =
            Pattern.compile("([0-9a-f]{4})\\s+: value (-?\\d+), min (-?\\d+), max (-?\\d+)");

    private Getevent() {}

    /** An axis's range and its value when getevent read it (at rest, normally). */
    record Range(int rest, int min, int max) {
        double centred(int value) {
            return Math.max(-1, Math.min(1, (value - (min + max) / 2.0) / ((max - min) / 2.0)));
        }

        double fraction(int value) {
            return Math.max(0, Math.min(1, (value - min) / (double) (max - min)));
        }

        boolean restsAtMin() {
            return fraction(rest) < 0.25;
        }
    }

    /** Where a pad reports its right stick and triggers; a trigger rests at its minimum, a stick at its centre. */
    record Axes(int rightX, int rightY, int leftTrigger, int rightTrigger) {
        static Axes of(Map<Integer, Range> ranges) {
            Range rx = ranges.get(ABS_RX);
            if (rx == null) return new Axes(ABS_Z, ABS_RZ, ABS_BRAKE, ABS_GAS);
            return rx.restsAtMin() ? new Axes(ABS_Z, ABS_RZ, ABS_RX, ABS_RY) : new Axes(ABS_RX, ABS_RY, ABS_Z, ABS_RZ);
        }
    }

    record Device(String path, String name, Set<Integer> keys, Map<Integer, Range> ranges) {
        boolean isGamepad() {
            return keys.contains(BTN_SOUTH) && ranges.containsKey(ABS_X) && ranges.containsKey(ABS_Y);
        }
    }

    static List<Device> parse(String output) {
        List<Device> devices = new ArrayList<>();
        String path = null, name = "", section = "";
        Set<Integer> keys = new HashSet<>();
        Map<Integer, Range> ranges = new HashMap<>();
        for (String line : output.split("\n")) {
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
                    ranges.put(Integer.parseInt(range.group(1), 16), new Range(Integer.parseInt(range.group(2)),
                            Integer.parseInt(range.group(3)), Integer.parseInt(range.group(4))));
                }
            }
        }
        if (path != null) devices.add(new Device(path, name, keys, ranges));
        return devices;
    }
}
