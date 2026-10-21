#!/usr/bin/env bash
# Downloads the MakeHuman assets the figure is built from into build/assets.
# Everything used is released under CC0 (public domain): the base mesh, targets, skeleton and
# weights from the MakeHuman repository, and the skin, hair, eyes, eyebrows, eyelashes, clothes
# and shoes from the "MakeHuman system assets" pack.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/assets
if [ ! -d build/assets/makehuman ]; then
  GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 https://github.com/makehumancommunity/makehuman build/assets/makehuman
fi
if [ ! -d build/assets/system ]; then
  curl -L --fail -o build/assets/system_cc0.zip \
    https://files.makehumancommunity.org/asset_packs/makehuman_system_assets/makehuman_system_assets_cc0.zip
  unzip -q build/assets/system_cc0.zip -d build/assets/system
fi
echo "assets ready in build/assets"
