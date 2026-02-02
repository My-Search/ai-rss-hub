#!/usr/bin/env bash
set -Eeuo pipefail

########################################
# Config
########################################
SQLITE_URL="https://www.sqlite.org/2026/sqlite-tools-linux-x64-3510200.zip"
TMP_DIR="/tmp/sqlite-install"
BIN_DIR="/usr/local/bin"
PROJECT_NAME="ai-rss-hub"
REPO_URL="https://github.com/My-Search/ai-rss-hub.git"

########################################
# Utils
########################################
log() {
  echo -e "\033[32m[INFO]\033[0m $*"
}

err() {
  echo -e "\033[31m[ERROR]\033[0m $*" >&2
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "Missing command: $1"; exit 1; }
}

########################################
# Check deps
########################################
need_cmd wget
need_cmd unzip
need_cmd git
need_cmd docker
need_cmd docker-compose

########################################
# Install sqlite if needed
########################################
install_sqlite() {
  if command -v sqlite3 >/dev/null 2>&1; then
    log "sqlite3 already installed: $(which sqlite3)"
    sqlite3 --version
    return
  fi

  log "Installing sqlite3..."

  rm -rf "$TMP_DIR"
  mkdir -p "$TMP_DIR"
  cd "$TMP_DIR"

  log "Downloading sqlite..."
  wget -q "$SQLITE_URL" -O sqlite.zip

  log "Unzipping..."
  unzip -q sqlite.zip

  log "Installing..."
  chmod +x sqlite-tools-linux-x64-*/*
  sudo mv sqlite-tools-linux-x64-*/* "$BIN_DIR/"

  log "Cleaning..."
  rm -rf "$TMP_DIR"

  log "sqlite installed:"
  sqlite3 --version
}

########################################
# Update project
########################################
update_project() {
  log "Updating project..."

  rm -rf "$PROJECT_NAME"
  git clone "$REPO_URL"

  if [ -d "data" ]; then
    cp -r data "$PROJECT_NAME/"
    ls -1 | grep -v -E '^(data|logs)$' | xargs rm -rf
  fi

  if [ -f "docker-compose.yml" ]; then
    cp -f docker-compose.yml "$PROJECT_NAME/"
  fi

  cp -rf "$PROJECT_NAME"/. .

  rm -rf "$PROJECT_NAME"
}

########################################
# DB update
########################################
update_db() {
  if [ -f "./data/rss.db" ] && [ -f "./update.sql" ]; then
    log "Updating database..."
    sqlite3 ./data/rss.db < ./update.sql
  else
    log "Skip db update."
  fi
}

########################################
# Docker deploy
########################################
deploy() {
  log "Building docker..."
  docker-compose build --no-cache

  log "Starting docker..."
  docker-compose up -d

  log "Deploy done."
}

########################################
# Main
########################################
main() {
  install_sqlite
  update_project
  update_db
  deploy
}

main "$@"
