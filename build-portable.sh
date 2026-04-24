#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

find_jdk_tool() {
    name="$1"
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/$name" ]; then
        printf '%s\n' "$JAVA_HOME/bin/$name"
        return 0
    fi

    if command -v "$name" >/dev/null 2>&1; then
        command -v "$name"
        return 0
    fi

    printf 'Could not find %s. Install a JDK with %s, or set JAVA_HOME.\n' "$name" "$name" >&2
    exit 1
}

BUILD_DIR="$SCRIPT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
JAR_FILE="$BUILD_DIR/NotebookMe.jar"
ROOT_JAR_FILE="$SCRIPT_DIR/NotebookMe.jar"
PORTABLE_DIR="$SCRIPT_DIR/dist/NotebookMe-portable"
APP_DIR="$PORTABLE_DIR/app"
DATA_DIR="$PORTABLE_DIR/data"
RUNTIME_DIR="$PORTABLE_DIR/runtime"

JAVAC=$(find_jdk_tool javac)
JAR=$(find_jdk_tool jar)
JDEPS=$(find_jdk_tool jdeps)
JLINK=$(find_jdk_tool jlink)

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"
find "$SCRIPT_DIR" -maxdepth 1 -name '*.java' -print | sort > "$BUILD_DIR/sources.txt"

printf '[1/4] Compiling Java sources...\n'
"$JAVAC" -encoding UTF-8 --release 11 -d "$CLASSES_DIR" @"$BUILD_DIR/sources.txt"

printf '[2/4] Packaging NotebookMe.jar...\n'
"$JAR" cfm "$JAR_FILE" "$SCRIPT_DIR/MANIFEST.MF" -C "$CLASSES_DIR" .
cp "$JAR_FILE" "$ROOT_JAR_FILE"

printf '[3/4] Creating portable folder...\n'
rm -rf "$PORTABLE_DIR"
mkdir -p "$APP_DIR" "$DATA_DIR"
cp "$JAR_FILE" "$APP_DIR/NotebookMe.jar"

MODULES=$("$JDEPS" --ignore-missing-deps --print-module-deps "$JAR_FILE" 2>/dev/null || printf 'java.base,java.desktop')
MODULES=$(printf '%s\n' "$MODULES" | awk 'NF { line=$0 } END { print line }')
[ -n "$MODULES" ] || MODULES="java.base,java.desktop"

case ",$MODULES," in
    *,java.desktop,*) ;;
    *) MODULES="$MODULES,java.desktop" ;;
esac

printf '[4/4] Bundling a minimal Java runtime (%s)...\n' "$MODULES"
"$JLINK" \
    --add-modules "$MODULES" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --output "$RUNTIME_DIR"

cat > "$PORTABLE_DIR/NotebookMe" <<'LAUNCHER'
#!/usr/bin/env sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_EXE="$APP_HOME/runtime/bin/java"

if [ ! -x "$JAVA_EXE" ]; then
    printf 'Portable runtime is missing: %s\n' "$JAVA_EXE" >&2
    exit 1
fi

mkdir -p "$APP_HOME/data"
exec "$JAVA_EXE" -Dnotebookme.portable=true "-Dnotebookme.dataDir=$APP_HOME/data" -jar "$APP_HOME/app/NotebookMe.jar" "$@"
LAUNCHER

cp "$PORTABLE_DIR/NotebookMe" "$PORTABLE_DIR/NotebookMe.command"
chmod +x "$PORTABLE_DIR/NotebookMe" "$PORTABLE_DIR/NotebookMe.command"

cat > "$PORTABLE_DIR/README-PORTABLE.txt" <<'README'
NotebookMe Portable
===================

Run:
  ./NotebookMe

On macOS, you can also run:
  ./NotebookMe.command

This folder is self-contained for this operating system:
  app/NotebookMe.jar     The application
  runtime/               Bundled Java runtime
  data/                  Notes, diary entries, drawings, and app data

No Java install is required on the computer that runs this folder.
Copy the whole NotebookMe-portable folder to another machine with the same OS
and CPU architecture.
README

printf 'Done. Portable app written to %s\n' "$PORTABLE_DIR"
