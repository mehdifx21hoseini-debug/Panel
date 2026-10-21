#!/usr/bin/env bash
# Renders every exercise in parallel browsers (JOBS, default 2), then encodes the videos for the app.
set -euo pipefail
cd "$(dirname "$0")"
KEYS=$(node -e "
  const fs = require('fs');
  const src = fs.readdirSync('web/exercises').filter((f) => f !== 'common.js').map((f) => fs.readFileSync('web/exercises/' + f, 'utf8')).join('\n');
  console.log([...src.matchAll(/^  ([a-z0-9_]+): \{/gm)].map((m) => m[1]).join(' '));
")
N=${JOBS:-2}; i=0; declare -a parts
for k in $KEYS; do parts[$((i % N))]+="$k "; i=$((i + 1)); done
for p in "${parts[@]}"; do node render.mjs --size ${SIZE:-512} $p & done
wait
python3 encode.py
