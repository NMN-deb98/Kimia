#!/usr/bin/env bash
# Fetches the pinned llama.cpp release into ./llama.cpp (used by CMake). Safe to re-run.
set -euo pipefail
TAG="b12219"
cd "$(dirname "$0")/.."
if [ -f llama.cpp/CMakeLists.txt ]; then echo "llama.cpp already present"; exit 0; fi
rm -rf llama.cpp
git clone --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp llama.cpp
