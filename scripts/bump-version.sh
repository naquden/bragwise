#!/usr/bin/env bash
# Bump app.versionName in gradle.properties and sync to iOS xcconfig.
# Usage: ./scripts/bump-version.sh [new-version]
# If no version given, reads current from gradle.properties and prints it.

set -euo pipefail

GRADLE_PROPS="gradle.properties"
XCCONFIG="iosApp/Configuration/Config.xcconfig"

current=$(grep '^app.versionName=' "$GRADLE_PROPS" | cut -d= -f2)

if [[ $# -eq 0 ]]; then
  echo "Current version: $current"
  echo "Usage: $0 <new-version>  (e.g. 0.7.5)"
  exit 0
fi

new=$1

# Validate semver-ish
if ! [[ $new =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Error: version must be x.y.z" >&2
  exit 1
fi

# Bump versionCode automatically (increment by 1)
current_code=$(grep '^app.versionCode=' "$GRADLE_PROPS" | cut -d= -f2)
new_code=$((current_code + 1))

sed -i '' "s/^app.versionName=.*/app.versionName=$new/" "$GRADLE_PROPS"
sed -i '' "s/^app.versionCode=.*/app.versionCode=$new_code/" "$GRADLE_PROPS"

sed -i '' "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=$new/" "$XCCONFIG"
sed -i '' "s/^MARKETING_VERSION=.*/MARKETING_VERSION=$new/" "$XCCONFIG"

echo "Bumped $current → $new (versionCode $current_code → $new_code)"
