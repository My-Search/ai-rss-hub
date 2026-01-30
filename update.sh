rm -rf ai-rss-hub
git clone https://github.com/My-Search/ai-rss-hub.git
cp -r data/ ai-rss-hub/
cp -rf docker-compose.yml ai-rss-hub/
cp -r ./ai-rss-hub/** ./
rm -rf ai-rss-hub/
docker-compose build --no-cache
docker-compose up -d
