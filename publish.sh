#!/bin/bash
# Publish the Claude Code JetBrains plugin to JetBrains Marketplace
#
# Usage:
#   export PUBLISH_TOKEN="your-marketplace-token"
#   ./publish.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -z "${PUBLISH_TOKEN:-}" ]; then
    echo "Error: PUBLISH_TOKEN environment variable is not set."
    echo "Get one from: https://hub.jetbrains.com/users/me?tab=authentification"
    exit 1
fi

export PRIVATE_KEY="$(cat "$SCRIPT_DIR/.keys/private.pem")"
export CERTIFICATE_CHAIN="$(cat "$SCRIPT_DIR/.keys/chain.crt")"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

echo "Signing and publishing plugin..."
"$SCRIPT_DIR/gradlew" signPlugin publishPlugin

echo "Done! Check https://plugins.jetbrains.com/plugin/manage for status."
