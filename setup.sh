#!/usr/bin/env bash
set -euo pipefail

# Run this script from the project root that contains Dockerfile and docker-compose.yml.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

IMAGE_NAME="${IMAGE_NAME:-astramut}"
HOSTNAME="$(id -un)"

export IMAGE_NAME
export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"
export HOST_PROJECT_DIR="$PWD"
export WORKSPACE_NAME="$(basename "$PWD")"
export HOST_SSH_DIR="${HOME}/.ssh"
export CONTAINER_NAME="astramut-${HOSTNAME}"

# Executables
COMPOSE=(docker compose)

mkdir -p "$HOST_SSH_DIR"
chmod 700 "$HOST_SSH_DIR" || true

echo "[1/3] Building image: ${IMAGE_NAME}"
"${COMPOSE[@]}" build dev

if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
  echo "[error] Image was not created: ${IMAGE_NAME}" >&2
  exit 1
fi

echo "[2/3] Creating container: ${CONTAINER_NAME}"
"${COMPOSE[@]}" up -d dev

echo "[3/3] Checking container status"
if ! docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  echo "[error] Container was not created: ${CONTAINER_NAME}" >&2
  "${COMPOSE[@]}" ps >&2 || true
  exit 1
fi

CONTAINER_STATUS="$(docker inspect -f '{{.State.Status}}' "$CONTAINER_NAME")"
if [[ "$CONTAINER_STATUS" != "running" ]]; then
  echo "[error] Container exists but is not running: ${CONTAINER_NAME} (${CONTAINER_STATUS})" >&2
  "${COMPOSE[@]}" logs --tail=80 dev >&2 || true
  exit 1
fi

CONTAINER_WORKDIR="/workspace/${WORKSPACE_NAME}"

echo "[ok] Image:      ${IMAGE_NAME}"
echo "[ok] Container:  ${CONTAINER_NAME} (${CONTAINER_STATUS})"
echo "[ok] Project:    ${HOST_PROJECT_DIR} -> ${CONTAINER_WORKDIR}"
echo

echo "Enter container with:"
echo "  docker exec -it ${CONTAINER_NAME} bash"
