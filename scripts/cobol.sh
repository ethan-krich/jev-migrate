#!/usr/bin/env bash
# Compiles COBOL with GnuCOBOL and runs it, the way the equivalence tests do.
#   ./scripts/cobol.sh [--data DIR] [cobc options...] MAIN.cbl [SUB.cbl ...]
# The main program comes first; cobc options (-I DIR, -free, ...) are passed through.
# Stdin and stdout pass through, and the exit status is the program's. Without --data the
# program runs in the current directory; with it, in a scratch copy of DIR's files.
# Without a local cobc, use the pinned GnuCOBOL 3.2: ./scripts/docker.sh cobol ...
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

usage="usage: ./scripts/cobol.sh [--data DIR] [cobc options...] MAIN.cbl [SUB.cbl ...]"
data=""
args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --data) [[ $# -ge 2 ]] || die "$usage"; data="$2"; shift 2 ;;
    -h|--help) echo "$usage"; exit 0 ;;
    *) args+=("$1"); shift ;;
  esac
done
[[ ${#args[@]} -gt 0 ]] || die "$usage"
have cobc || die "cobc (GnuCOBOL) not found; use ./scripts/docker.sh cobol ... for the pinned 3.2"

tmp="$(mktemp -d "${TMPDIR:-/tmp}"/cobc-XXXXXX)"
trap 'rm -rf "$tmp"' EXIT
exe="$tmp/program"
cobc -x -o "$exe" "${args[@]}"

run_dir="$PWD"
if [[ -n "$data" ]]; then
  [[ -d "$data" ]] || die "--data $data is not a directory"
  run_dir="$tmp/run"
  mkdir "$run_dir"
  find "$data" -maxdepth 1 -type f -exec cp {} "$run_dir/" \;
fi
status=0
(cd "$run_dir" && "$exe") || status=$?
exit "$status"
