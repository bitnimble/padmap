#!/system/bin/sh
# Starts (or restarts) the Padmap daemon. As shell: adb shell sh <this file>. As root: su -c sh <this file>.
LOG=/data/local/tmp/padmap.log
APK=$(pm path app.padmap | sed -n 's/^package://p' | head -n 1)
[ -n "$APK" ] || { echo "Padmap isn't installed" >&2; exit 1; }
# pidof, not pkill: app_process renames comm to "main"; only the cmdline says padmap
OLD=$(pidof padmap)
if [ -n "$OLD" ]; then
    kill $OLD 2>/dev/null
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        pidof padmap >/dev/null || break
        sleep 0.5
    done
    if pidof padmap >/dev/null; then
        echo "The running Padmap ($OLD) didn't stop; if it was started as root, restart it as root." >&2
        exit 1
    fi
fi
rm -f "$LOG"
CLASSPATH="$APK" setsid nohup app_process / --nice-name=padmap app.padmap.daemon.Main </dev/null >"$LOG" 2>&1 &
sleep 2
if pidof padmap >/dev/null; then
    echo "Padmap is running. You can unplug now."
else
    echo "Padmap didn't start:" >&2
    cat "$LOG" >&2
    exit 1
fi
