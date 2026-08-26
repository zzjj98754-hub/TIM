#!/usr/bin/env sh
set -e

MAVEN_VERSION="3.9.12"
MAVEN_DIST_NAME="apache-maven-${MAVEN_VERSION}"
MAVEN_WRAPPER_DIST_DIR="${HOME}/.m2/wrapper/dists"
MAVEN_CMD=""

for candidate in "${MAVEN_WRAPPER_DIST_DIR}"/apache-maven-*/bin/mvn; do
  if [ -x "$candidate" ]; then
    MAVEN_CMD="$candidate"
    break
  fi
done

if [ -z "$MAVEN_CMD" ]; then
  DOWNLOAD_DIR="${TMPDIR:-/tmp}/tim-maven-wrapper"
  ZIP_FILE="${DOWNLOAD_DIR}/${MAVEN_DIST_NAME}-bin.zip"
  EXTRACT_DIR="${DOWNLOAD_DIR}/extract"
  mkdir -p "$DOWNLOAD_DIR" "$EXTRACT_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/${MAVEN_DIST_NAME}-bin.zip" -o "$ZIP_FILE"
  fi
  unzip -oq "$ZIP_FILE" -d "$EXTRACT_DIR"
  MAVEN_CMD="${EXTRACT_DIR}/${MAVEN_DIST_NAME}/bin/mvn"
fi

exec "$MAVEN_CMD" "$@"
