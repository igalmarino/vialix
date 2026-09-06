#!/usr/bin/env bash
# Installs the Android SDK pieces Vialix needs (see README "Build and run").
# Idempotent: safe to re-run; sdkmanager skips packages that are already installed.
set -euo pipefail

SDK="${ANDROID_HOME:-/opt/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools into $SDK"
  sudo mkdir -p "$SDK/cmdline-tools"
  sudo chown -R "$(id -u):$(id -g)" "$SDK"
  curl -sSL -o "/tmp/$TOOLS_ZIP" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  unzip -q "/tmp/$TOOLS_ZIP" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f "/tmp/$TOOLS_ZIP"
fi

# Volume-mounted dirs are created by Docker as root on first use.
sudo chown -R "$(id -u):$(id -g)" "$HOME/.gradle" "$HOME/.claude" "$HOME/.config/gh" 2>/dev/null || true

yes | "$SDKMANAGER" --licenses > /dev/null || true
"$SDKMANAGER" "platforms;android-36" "build-tools;36.0.0" "platform-tools"

echo "Android SDK ready at $SDK"
