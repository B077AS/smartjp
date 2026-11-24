#!/usr/bin/env bash
# Checks that the Java runtime jpack built holds every JDK module the app needs. `modules` in src/jpack/jpack.json is a hand-kept list
# (jpack's own detection cannot look inside a Spring Boot jar, whose libraries are nested), and a missing module only shows at run time as a
# NoClassDefFoundError: the app starts but stays empty. So the release job runs jdeps over the app's own classes and every library
# in BOOT-INF/lib and compares the result with the runtime that was really built.
#
#   check-runtime-modules.sh <the app jar> <the java executable of the runtime, e.g. target/jpack/image/linux-x64/runtime/bin/java>
set -euo pipefail

jar="${1:?usage: check-runtime-modules.sh <app jar> <runtime java executable>}"
runtime_java="${2:?usage: check-runtime-modules.sh <app jar> <runtime java executable>}"

jar="$(cd "$(dirname "$jar")" && pwd)/$(basename "$jar")"
runtime_java="$(cd "$(dirname "$runtime_java")" && pwd)/$(basename "$runtime_java")"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -q "$jar" -d "$work"

cd "$work"
need="$(jdeps --multi-release 25 --ignore-missing-deps --print-module-deps --class-path 'BOOT-INF/lib/*' BOOT-INF/classes | tr ',' '\n')"
have="$("$runtime_java" --list-modules | sed 's/@.*//')"

missing=0
for m in $need; do
  if ! echo "$have" | grep -qx "$m"; then
    echo "the runtime is missing $m: add it to modules in src/jpack/jpack.json" >&2
    missing=1
  fi
done
[ "$missing" = 0 ] || exit 1
echo "the runtime holds every module the app needs: $(echo "$need" | tr '\n' ' ')"
