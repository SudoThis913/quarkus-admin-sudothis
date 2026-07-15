#!/bin/bash
#
# DEPRECATED: superseded by deploy/deploy-pi.sh (Raspberry Pi compose deployment).
# Kept for reference; the uber-jar + Dockerfile.jvm combination here is broken
# (Dockerfile.jvm expects the fast-jar quarkus-app/ layout).
set -e

# Configuration
APP_NAME="quarkus-admin"
TAG="prod"
IMAGE_NAME="${APP_NAME}:${TAG}"
PLATFORM="linux/arm64" # Target Raspberry Pi 4

echo "🚀 Preparing Production Deployment for SudoThis.com (RPi 4)"

# 1. Build the Application (Java)
echo "☕ Building Quarkus App (Uber Jar)..."
./mvnw package -Dquarkus.package.jar.type=uber-jar -DskipTests

# 2. Build Docker Image (Multi-arch or specific)
echo "Whale Building Docker Image for ${PLATFORM}..."
# Ensure buildx is available for cross-compilation if not on arm64
if [ "$(uname -m)" != "aarch64" ]; then
    echo "   Detected non-ARM architecture. Using docker buildx..."
    # Create builder if not exists (optional check)
    # docker buildx create --use || true
    docker buildx build --platform ${PLATFORM} -f src/main/docker/Dockerfile.jvm -t ${IMAGE_NAME} --load .
else
    docker build -f src/main/docker/Dockerfile.jvm -t ${IMAGE_NAME} .
fi

echo "✅ Image ${IMAGE_NAME} built."

# 3. Terraform Deployment
echo "🌍 Applying Terraform configuration..."
cd terraform/environments/prod

# Check for secrets
if [ ! -f secrets.auto.tfvars ]; then
    echo "⚠️  secrets.auto.tfvars not found!"
    echo "   Please create it with: db_password, redis_password"
    echo "   Example: echo 'db_password=\"secure\"' > secrets.auto.tfvars"
    exit 1
fi

terraform init
terraform apply -auto-approve -var="app_image=${IMAGE_NAME}"

echo "🎉 Deployment Complete! Visit https://SudoThis.com"
