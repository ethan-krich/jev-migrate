#!/usr/bin/env bash
# Compiles translated Java and runs it, the way the equivalence tests do.
#   ./scripts/java.sh [--data DIR] [--main CLASS] OUTPUT_DIR
# OUTPUT_DIR is a run.sh --output directory; every .java file under src/main/java is compiled
# (javac -Xlint:all -Werror) into OUTPUT_DIR/classes. --main defaults to the only class that has a
# main method. Stdin and stdout pass through, and the exit status is the program's. Without
# --data the program runs in the current directory; with it, in a scratch copy of DIR's files.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

usage="usage: ./scripts/java.sh [--data DIR] [--main CLASS] OUTPUT_DIR"
data=""
main=""
output=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --data) [[ $# -ge 2 ]] || die "$usage"; data="$2"; shift 2 ;;
    --main) [[ $# -ge 2 ]] || die "$usage"; main="$2"; shift 2 ;;
    -h|--help) echo "$usage"; exit 0 ;;
    -*) die "unknown option '$1'; $usage" ;;
    *) [[ -z "$output" ]] || die "$usage"; output="$1"; shift ;;
  esac
done
[[ -n "$output" ]] || die "$usage"
src="$output/src/main/java"
[[ -d "$src" ]] || die "$src not found; translate first with ./scripts/run.sh SOURCE... --output $output"
have javac || die "javac not found; install a full JDK"

sources=()
while IFS= read -r f; do sources+=("$f"); done < <(find "$src" -name '*.java' | sort)
[[ ${#sources[@]} -gt 0 ]] || die "no .java files under $src"

if [[ -z "$main" ]]; then
  mains=()
  while IFS= read -r f; do mains+=("$f"); done < <(grep -lE 'static[[:space:]]+void[[:space:]]+main[[:space:]]*\(' "${sources[@]}" || true)
  names=()
  for f in ${mains[@]+"${mains[@]}"}; do f="${f#"$src"/}"; f="${f%.java}"; names+=("${f//\//.}"); done
  [[ ${#names[@]} -eq 1 ]] || die "pass --main CLASS; classes with a main method: ${names[*]:-none}"
  main="${names[0]}"
fi

classes="$(cd "$output" && pwd)/classes"
javac -encoding UTF-8 -Xlint:all -Werror -d "$classes" "${sources[@]}"

run_dir="$PWD"
if [[ -n "$data" ]]; then
  [[ -d "$data" ]] || die "--data $data is not a directory"
  tmp="$(mktemp -d "${TMPDIR:-/tmp}"/java-XXXXXX)"
  trap 'rm -rf "$tmp"' EXIT
  run_dir="$tmp"
  find "$data" -maxdepth 1 -type f -exec cp {} "$run_dir/" \;
fi
status=0
(cd "$run_dir" && java -cp "$classes" "$main") || status=$?
exit "$status"
