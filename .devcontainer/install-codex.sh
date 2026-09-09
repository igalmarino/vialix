#!/usr/bin/env bash
# Installs Codex with the official standalone installer into ~/.local/bin.
# The payload lives under ~/.codex, persisted by the dev container volume.
# Re-running after a rebuild restores the launcher if only the payload survived.
set -euo pipefail

if [ -x "$HOME/.local/bin/codex" ]; then
  echo "Codex already installed: $("$HOME/.local/bin/codex" --version)"
  exit 0
fi

echo "Installing Codex into $HOME/.local/bin"
curl -fsSL https://chatgpt.com/codex/install.sh | sh
"$HOME/.local/bin/codex" --version
