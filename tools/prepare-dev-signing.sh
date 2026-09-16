#!/usr/bin/env bash
set -euo pipefail
mkdir -p dev-signing
base64 -d dev-signing/yamone-dev.keystore.b64 > dev-signing/yamone-dev.keystore
