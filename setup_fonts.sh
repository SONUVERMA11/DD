#!/bin/bash
# DD — Deep Downloader: Font Setup Script
# Downloads Plus Jakarta Sans and DM Sans from Google Fonts

FONT_DIR="app/src/main/res/font"
mkdir -p "$FONT_DIR"

echo "Downloading Plus Jakarta Sans..."
curl -L -o /tmp/plus_jakarta_sans.zip "https://fonts.google.com/download?family=Plus+Jakarta+Sans"
unzip -o /tmp/plus_jakarta_sans.zip -d /tmp/plus_jakarta_sans/

# Copy static font files and rename to android-compatible names
cp "/tmp/plus_jakarta_sans/static/PlusJakartaSans-Regular.ttf" "$FONT_DIR/plus_jakarta_sans_regular.ttf"
cp "/tmp/plus_jakarta_sans/static/PlusJakartaSans-Medium.ttf" "$FONT_DIR/plus_jakarta_sans_medium.ttf"
cp "/tmp/plus_jakarta_sans/static/PlusJakartaSans-SemiBold.ttf" "$FONT_DIR/plus_jakarta_sans_semibold.ttf"
cp "/tmp/plus_jakarta_sans/static/PlusJakartaSans-Bold.ttf" "$FONT_DIR/plus_jakarta_sans_bold.ttf"
cp "/tmp/plus_jakarta_sans/static/PlusJakartaSans-ExtraBold.ttf" "$FONT_DIR/plus_jakarta_sans_extrabold.ttf"

echo "Downloading DM Sans..."
curl -L -o /tmp/dm_sans.zip "https://fonts.google.com/download?family=DM+Sans"
unzip -o /tmp/dm_sans.zip -d /tmp/dm_sans/

cp "/tmp/dm_sans/static/DMSans-Regular.ttf" "$FONT_DIR/dm_sans_regular.ttf"
cp "/tmp/dm_sans/static/DMSans-Medium.ttf" "$FONT_DIR/dm_sans_medium.ttf"
cp "/tmp/dm_sans/static/DMSans-SemiBold.ttf" "$FONT_DIR/dm_sans_semibold.ttf"
cp "/tmp/dm_sans/static/DMSans-Bold.ttf" "$FONT_DIR/dm_sans_bold.ttf"

echo "✅ Fonts downloaded and placed in $FONT_DIR"
echo ""
echo "Font files created:"
ls -la "$FONT_DIR"/*.ttf 2>/dev/null || echo "  (download may have failed — place font files manually)"
