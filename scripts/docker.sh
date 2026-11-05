#!/usr/bin/env bash
# Runs the scripts inside the pinned toolchain image (docker/Dockerfile).
#   ./scripts/docker.sh build|test|run|setup|cobol|java [args...]   run that script in the container
#   ./scripts/docker.sh shell                            interactive shell
#   ./scripts/docker.sh image                            (re)build the image only
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
have docker || die "docker is not installed"

IMAGE="transpiler-dev:$(cksum < "$ROOT/docker/Dockerfile" | cut -d' ' -f1)"
if [[ "${1:-}" == "image" ]] || ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  info "building $IMAGE"
  docker build -t "$IMAGE" "$ROOT/docker" >&2
  [[ "${1:-}" == "image" ]] && exit 0
fi

# The cache volume is created root-owned; hand it to the invoking user (cheap after the first run).
docker run --rm -v transpiler-dev-home:/home/dev "$IMAGE" chown -R "$(id -u):$(id -g)" /home/dev

cmd="${1:-test}"; shift || true
# stdin stays open (-i) so piped or redirected input reaches ACCEPT; a TTY (-t) only when both ends are terminals
tty=(-i); [[ -t 0 && -t 1 ]] && tty=(-it)
run=(docker run --rm "${tty[@]}" --user "$(id -u):$(id -g)"
     -v "$ROOT:/work" -v transpiler-dev-home:/home/dev -w /work
     -e TRANSPILER_CORE=/work/build/docker/core/transpiler-core "$IMAGE")
case "$cmd" in
  shell) exec "${run[@]}" bash ;;
  setup|build|test|run|cobol|java) exec "${run[@]}" "./scripts/$cmd.sh" "$@" ;;
  *) exec "${run[@]}" "$cmd" "$@" ;;
esac
