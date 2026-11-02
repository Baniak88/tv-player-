#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -lt 2 ]; then
  echo 'Usage: bash scripts/install-samsung.sh SAMSUNG_CERTIFICATE_PROFILE TV_TARGET_NAME'
  echo 'Create the profile in Tizen Studio Certificate Manager using the TV DUID. Connect the TV with sdb first.'
  exit 2
fi
command -v tizen >/dev/null || { echo 'Install Tizen Studio, Samsung TV Extension and Samsung Certificate Extension first.'; exit 1; }
tv_profile="$1"
tv_target="$2"
task_root="$(cd "$(dirname "$0")/.." && pwd)"
task_stage="$(mktemp -d)"
trap 'rm -rf "$task_stage"' EXIT
python3 "$task_root/scripts/package-samsung.py" --output "$task_stage/source.wgt"
python3 - "$task_stage" <<'PY'
import sys,zipfile,pathlib
root=pathlib.Path(sys.argv[1]);(root/'widget').mkdir()
with zipfile.ZipFile(root/'source.wgt') as widget: widget.extractall(root/'widget')
PY
tizen build-web -- "$task_stage/widget"
tv_build="$task_stage/widget/.buildResult"
test -d "$tv_build" || { echo "Tizen build-web did not create .buildResult."; exit 1; }
tizen package -t wgt -s "$tv_profile" -- "$tv_build"
tv_widget="$(find "$tv_build" -maxdepth 1 -name '*.wgt' -print -quit)"
test -n "$tv_widget" || { echo 'The Tizen CLI did not create a signed WGT.'; exit 1; }
python3 - "$tv_widget" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as package:
    names=set(package.namelist())
    if 'author-signature.xml' not in names or 'signature1.xml' not in names: raise SystemExit('Refusing to install: Samsung signatures are missing.')
PY
mkdir -p "$task_root/dist"
cp "$tv_widget" "$task_root/dist/TV-Player-Samsung-0.9.0-signed.wgt"
tizen install -n "$(basename "$tv_widget")" -t "$tv_target" -- "$(dirname "$tv_widget")"
echo 'Installed. Start TV Player from Apps on the TV.'
