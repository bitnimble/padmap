package app.padmap.daemon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

final class Store {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    // root outside an app's mount namespace (Lepton, su) has no /sdcard; the backing dir is the same storage
    private static final String[] ROOTS = {"/sdcard", "/storage/emulated/0", "/data/media/0"};

    private final File dir;

    Store() {
        File root = new File(ROOTS[ROOTS.length - 1]);
        for (String candidate : ROOTS) {
            if (new File(candidate).isDirectory()) {
                root = new File(candidate);
                break;
            }
        }
        dir = new File(root, "Padmap");
    }

    static boolean validPackage(String name) {
        return name != null && name.length() <= 255 && PACKAGE.matcher(name).matches();
    }

    File file(String pkg) {
        if (!validPackage(pkg)) throw new IllegalArgumentException("bad package name: " + pkg);
        return new File(dir, pkg + ".json");
    }

    String read(String pkg) throws IOException {
        return new String(Files.readAllBytes(file(pkg).toPath()), StandardCharsets.UTF_8);
    }

    synchronized void write(String pkg, String json) throws IOException {
        File target = file(pkg);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't create " + dir);
        File temp = new File(dir, "." + pkg + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!temp.renameTo(target)) throw new IOException("can't replace " + target);
    }
}
