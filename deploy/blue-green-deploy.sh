#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/home/taeho/app"
NGINX_CONF="/etc/nginx/sites-available/dev-runtaeho"

BLUE_PORT="12642"
GREEN_PORT="12644"
VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
    echo "Usage: $0 <version>" >&2
    exit 2
fi

if [[ ! -f "$APP_DIR/.env" || ! -f "$APP_DIR/Dockerfile" || ! -f "$APP_DIR/running-1.0.0.jar" ]]; then
    echo "Missing /home/taeho/app/.env, Dockerfile, or running-1.0.0.jar" >&2
    exit 1
fi

if [[ ! -f "$NGINX_CONF" ]]; then
    echo "Missing $NGINX_CONF" >&2
    exit 1
fi

if ! grep -Eq "proxy_pass http://127\\.0\\.0\\.1:(${BLUE_PORT}|${GREEN_PORT});" "$NGINX_CONF"; then
    echo "Expected proxy_pass for port $BLUE_PORT or $GREEN_PORT in $NGINX_CONF" >&2
    exit 1
fi

if grep -q "proxy_pass http://127.0.0.1:${GREEN_PORT};" "$NGINX_CONF"; then
    active_color="green"
    active_port="$GREEN_PORT"
    next_color="blue"
    next_port="$BLUE_PORT"
else
    active_color="blue"
    active_port="$BLUE_PORT"
    next_color="green"
    next_port="$GREEN_PORT"
fi

active_container="running_back_${active_color}"
next_container="running_back_${next_color}"
image="running-back:${VERSION}"

echo "[1/6] Build image: $image"
docker build --build-arg JAR_FILE=running-1.0.0.jar -t "$image" "$APP_DIR"

echo "[2/6] Start $next_color container"
docker rm -f "$next_container" >/dev/null 2>&1 || true
docker run -d \
    --name "$next_container" \
    --restart unless-stopped \
    --network running-dev \
    --env-file "$APP_DIR/.env" \
    -e "SERVER_TYPE=$next_color" \
    -e "SERVER_PORT=8080" \
    -p "127.0.0.1:${next_port}:8080" \
    "$image" >/dev/null

echo "[3/6] Check $next_color health"
for _ in $(seq 1 30); do
    if curl -fsS "http://127.0.0.1:${next_port}/healthy-check" >/dev/null \
        && [[ "$(curl -fsS "http://127.0.0.1:${next_port}/active-type")" == "$next_color" ]]; then
        health_ok="true"
        break
    fi
    sleep 2
done

if [[ "${health_ok:-false}" != "true" ]]; then
    echo "$next_color health check failed. Keeping $active_color active." >&2
    docker logs "$next_container" --tail 200 >&2 || true
    docker rm -f "$next_container" >/dev/null 2>&1 || true
    exit 1
fi

echo "[4/6] Switch Nginx to $next_color"
sudo sed -i -E "s#proxy_pass http://127\\.0\\.0\\.1:(${BLUE_PORT}|${GREEN_PORT});#proxy_pass http://127.0.0.1:${next_port};#" "$NGINX_CONF"
if ! sudo nginx -t; then
    echo "Nginx config check failed. Rolling back proxy_pass." >&2
    sudo sed -i -E "s#proxy_pass http://127\\.0\\.0\\.1:(${BLUE_PORT}|${GREEN_PORT});#proxy_pass http://127.0.0.1:${active_port};#" "$NGINX_CONF"
    docker rm -f "$next_container" >/dev/null 2>&1 || true
    exit 1
fi
sudo systemctl reload nginx

echo "[5/6] Verify Nginx"
for _ in $(seq 1 15); do
    if [[ "$(curl -k -fsS https://127.0.0.1:12643/active-type)" == "$next_color" ]]; then
        nginx_ok="true"
        break
    fi
    sleep 1
done

if [[ "${nginx_ok:-false}" != "true" ]]; then
    echo "Nginx verification failed. Rolling back to $active_color." >&2
    sudo sed -i -E "s#proxy_pass http://127\\.0\\.0\\.1:(${BLUE_PORT}|${GREEN_PORT});#proxy_pass http://127.0.0.1:${active_port};#" "$NGINX_CONF"
    sudo nginx -t
    sudo systemctl reload nginx
    docker rm -f "$next_container" >/dev/null 2>&1 || true
    exit 1
fi

echo "[6/6] Stop $active_color container after drain"
sleep 30
docker stop --time 40 "$active_container" >/dev/null 2>&1 || true
docker rm "$active_container" >/dev/null 2>&1 || true
docker stop --time 40 running_back >/dev/null 2>&1 || true
docker rm running_back >/dev/null 2>&1 || true

echo "Deployment complete. Active color: $next_color"
