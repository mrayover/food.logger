#!/usr/bin/env bash
set -euo pipefail
exec > >(tee deliverables/release/install-smoke.txt) 2>&1
apk=deliverables/release/Food.Logger-release.apk
adb install "$apk"
adb shell am start -W -n com.foodlogger/.MainActivity | tee /tmp/foodlogger-launch.txt
grep -q 'Status: ok' /tmp/foodlogger-launch.txt
sleep 3
adb shell pidof com.foodlogger
before=$(adb shell dumpsys package com.foodlogger | grep 'userId=' | head -1)
adb install -r "$apk"
after=$(adb shell dumpsys package com.foodlogger | grep 'userId=' | head -1)
test "$before" = "$after"
adb shell am force-stop com.foodlogger
adb shell am start -W -n com.foodlogger/.MainActivity | tee /tmp/foodlogger-reopen.txt
grep -q 'Status: ok' /tmp/foodlogger-reopen.txt
sleep 3
adb shell pidof com.foodlogger
echo 'PASS: install, launch, reinstall -r with unchanged UID, and relaunch.'
echo 'No real-user meal preservation or owner phone acceptance is asserted by this smoke test.'
