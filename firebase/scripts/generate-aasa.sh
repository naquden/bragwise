#!/usr/bin/env bash
# Generates apple-app-site-association from TEAM_ID env var.
# Run before `firebase deploy --only hosting`.
#
#   TEAM_ID=ABCDE12345 ./firebase/scripts/generate-aasa.sh
#
# Bundle ID convention from iosApp/Configuration/Config.xcconfig:
#   PRODUCT_BUNDLE_IDENTIFIER = se.atte.bragwise.Bragwise
set -euo pipefail

if [[ -z "${TEAM_ID:-}" ]]; then
  echo "TEAM_ID env var required" >&2
  exit 1
fi

BUNDLE_ID="se.atte.bragwise.Bragwise"
APP_ID="${TEAM_ID}.${BUNDLE_ID}"
OUT="$(dirname "$0")/../public/.well-known/apple-app-site-association"

cat > "$OUT" <<EOF
{
  "applinks": {
    "details": [
      {
        "appIDs": ["${APP_ID}"],
        "components": [
          { "/": "*" }
        ]
      }
    ]
  }
}
EOF

echo "Wrote $OUT with appID ${APP_ID}"
