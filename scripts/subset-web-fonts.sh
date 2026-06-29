#!/usr/bin/env bash
# Subsets Noto Sans Devanagari and Noto Sans SC to the glyphs used by the
# Hindi and Chinese (Simplified) translations + native display names.
# Re-run whenever values-hi/ or values-zh-rCN/ strings change.
#
# Requires: pyftsubset (pip install fonttools or `brew install fonttools`)
# Source fonts must be present in temp/ — see download instructions below.
#
# Download source fonts (OFL licensed) from Google Fonts:
#   curl -L "https://fonts.gstatic.com/s/notosansdevanagari/v30/TuGoUUFzXI5FBtUq5a8bjKYTZjtRU6Sgv3NaV_SNmI0b8QQCQmHn6B2OHjbL_08AlXQly-A.ttf" \
#        -o temp/NotoSansDevanagari-Regular.ttf
#   curl -L "https://fonts.gstatic.com/s/notosanssc/v40/k3kCo84MPvpLmixcA63oeAL7Iqp5IZJF9bmaG9_FnYw.ttf" \
#        -o temp/NotoSansSC-Regular.ttf
# Place the .ttf files at:
#   temp/NotoSansDevanagari-Regular.ttf
#   temp/NotoSansSC-Regular.ttf

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FONT_DIR="$REPO_ROOT/shared/src/commonMain/composeResources/font"
TEMP_DIR="$REPO_ROOT/temp"
VALUES_HI="$REPO_ROOT/shared/src/commonMain/composeResources/values-hi/strings.xml"
VALUES_ZH="$REPO_ROOT/shared/src/commonMain/composeResources/values-zh-rCN/strings.xml"

# Extract text content from XML (strip tags, decode entities)
extract_text() {
    sed 's/<[^>]*>//g' "$1" \
        | sed 's/&amp;/\&/g; s/&lt;/</g; s/&gt;/>/g; s/&apos;/'"'"'/g; s/&quot;/"/g' \
        | tr -s ' \n\t' '\n' \
        | sort -u
}

echo "Extracting Hindi text..."
{
    extract_text "$VALUES_HI"
    printf 'हिन्दी\n'  # native display name
} > "$TEMP_DIR/hi_glyphs.txt"

echo "Extracting Chinese text..."
{
    extract_text "$VALUES_ZH"
    printf '中文（简体）\n'  # native display name
} > "$TEMP_DIR/zh_glyphs.txt"

DEVANAGARI_SRC="$TEMP_DIR/NotoSansDevanagari-Regular.ttf"
ZH_SRC="$TEMP_DIR/NotoSansSC-Regular.ttf"

if [[ ! -f "$DEVANAGARI_SRC" ]]; then
    echo "ERROR: $DEVANAGARI_SRC not found. Download from Google Fonts (see script header)."
    exit 1
fi
if [[ ! -f "$ZH_SRC" ]]; then
    echo "ERROR: $ZH_SRC not found. Download from Google Fonts (see script header)."
    exit 1
fi

echo "Subsetting Devanagari font..."
pyftsubset "$DEVANAGARI_SRC" \
    --text-file="$TEMP_DIR/hi_glyphs.txt" \
    --layout-features='*' \
    --output-file="$FONT_DIR/noto_sans_devanagari_subset.ttf"

echo "Subsetting Chinese font..."
pyftsubset "$ZH_SRC" \
    --text-file="$TEMP_DIR/zh_glyphs.txt" \
    --layout-features='*' \
    --output-file="$FONT_DIR/noto_sans_sc_subset.ttf"

echo "Done."
ls -lh "$FONT_DIR/noto_sans_devanagari_subset.ttf" "$FONT_DIR/noto_sans_sc_subset.ttf"
