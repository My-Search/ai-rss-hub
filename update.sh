set -e

if command -v sqlite3 >/dev/null 2>&1; then
    echo "sqlite3 already installed: $(which sqlite3)"
    sqlite3 --version
    exit 0
fi

URL="https://www.sqlite.org/2026/sqlite-tools-linux-x64-3510200.zip"
TMP_DIR="/tmp/sqlite-install"
BIN_DIR="/usr/local/bin"

rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"
cd "$TMP_DIR"

echo "==> Downloading sqlite..."
wget -q "$URL" -O sqlite.zip

echo "==> Unzipping..."
unzip -q sqlite.zip

echo "==> Installing..."
chmod +x sqlite-tools-linux-x64-*/*
sudo mv sqlite-tools-linux-x64-*/* "$BIN_DIR/"

echo "==> Cleaning..."
rm -rf "$TMP_DIR"

echo "==> Installed."
sqlite3 --version
which sqlite3





rm -rf ai-rss-hub
git clone https://github.com/My-Search/ai-rss-hub.git
cp -r data/ ai-rss-hub/
cp -rf docker-compose.yml ai-rss-hub/
cp -r ./ai-rss-hub/** ./
rm -rf ai-rss-hub/
# 数据库更新
sqlite3 ./data/rss.db < ./update.sql
docker-compose build --no-cache
docker-compose up -d
