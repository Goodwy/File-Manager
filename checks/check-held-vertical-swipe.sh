#!/usr/bin/env bash
# Invoke as a child shell: bash checks/check-held-vertical-swipe.sh
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
compiler_cache="${FILE_MANAGER_KOTLIN_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/file-manager-kotlin/2.3.10}"
mkdir -p "$compiler_cache"
artifacts=(
  org/jetbrains/kotlin/kotlin-compiler-embeddable/2.3.10/kotlin-compiler-embeddable-2.3.10.jar
  org/jetbrains/kotlin/kotlin-stdlib/2.3.10/kotlin-stdlib-2.3.10.jar
  org/jetbrains/kotlin/kotlin-script-runtime/2.3.10/kotlin-script-runtime-2.3.10.jar
  org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar
  org/jetbrains/kotlin/kotlin-daemon-embeddable/2.3.10/kotlin-daemon-embeddable-2.3.10.jar
  org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0/kotlinx-coroutines-core-jvm-1.8.0.jar
  org/jetbrains/annotations/13.0/annotations-13.0.jar
)
for artifact in "${artifacts[@]}"; do
  jar_path="$compiler_cache/${artifact##*/}"
  if [[ ! -s "$jar_path" ]]; then
    curl --fail --location --retry 2 --max-time 180 --silent --show-error \
      "https://repo.maven.apache.org/maven2/$artifact" --output "$jar_path.part"
    mv -- "$jar_path.part" "$jar_path"
  fi
done
check_tmp="$(mktemp -d)"
trap 'rm -rf -- "$check_tmp"' EXIT
java -cp "$compiler_cache/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 \
  -classpath "$compiler_cache/kotlin-stdlib-2.3.10.jar" \
  -d "$check_tmp/checks.jar" \
  "$repo_dir/app/src/main/kotlin/com/goodwy/filemanager/views/HeldVerticalSwipeTracker.kt" \
  "$repo_dir/checks/HeldVerticalSwipeChecks.kt"
java -cp "$check_tmp/checks.jar:$compiler_cache/kotlin-stdlib-2.3.10.jar" \
  com.goodwy.filemanager.views.HeldVerticalSwipeChecksKt
