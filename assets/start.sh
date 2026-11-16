#!/system/bin/sh
# Starts (or restarts) the Padmap daemon. As shell: adb shell sh <this file>. As root: su -c sh <this file>.
LOG=/data/local/tmp/padmap.log
APK=$(pm path app.padmap | sed -n 's/^package://p' | head -n 1)
[ -n "$APK" ] || { echo "Padmap isn't installed" >&2; exit 1; }
pkill -x padmap && sleep 1
CLASSPATH="$APK" setsid nohup app_process / --nice-name=padmap app.padmap.daemon.Main </dev/null >"$LOG" 2>&1 &
sleep 2
if pidof padmap >/dev/null; then
    echo "Padmap is running. You can unplug now."
else
    echo "Padmap didn't start:" >&2
    cat "$LOG" >&2
    exit 1
fi
