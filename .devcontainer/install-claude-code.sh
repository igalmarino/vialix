#!/usr/bin/env bash
# Installs Claude Code with the native installer (https://claude.ai/install.sh) into
# ~/.local/bin, owned by the container user so the CLI can auto-update itself.
# The claude-code dev container feature is not used on purpose: it runs `npm install -g`
# as root during the image build, leaving the package root-owned and the auto-updater
# failing with "no write permission to npm prefix".
# Idempotent: skipped when a native install is already present (it keeps itself current).
set -euo pipefail

if [ -x "$HOME/.local/bin/claude" ]; then
  echo "Claude Code already installed: $("$HOME/.local/bin/claude" --version)"
  exit 0
fi

echo "Installing Claude Code into $HOME/.local/bin"
curl -fsSL https://claude.ai/install.sh | bash
"$HOME/.local/bin/claude" --version
