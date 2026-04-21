#!/bin/bash
set -e

echo "==> Building all services..."
./mvnw clean package -DskipTests

echo "==> Building Docker images and starting services..."
docker-compose up -d --build "$@"

echo "==> Done. Services:"
docker-compose ps
