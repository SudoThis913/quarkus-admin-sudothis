#!/bin/bash
set -e

# Color codes for output
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}Initializing Quarkus Admin Environment...${NC}"

# ------------------------------------------------------------------
# 1. Secrets Generation
# ------------------------------------------------------------------
if [ -f .env ]; then
    echo ".env already exists. Sourcing it..."
    source .env
else
    echo "Generating new .env file with strong secrets..."
    # Generate random passwords
    DB_PASSWORD=$(openssl rand -base64 24)
    REDIS_PASSWORD=$(openssl rand -base64 24)
    
    cat <<EOF > .env
# Database Credentials
DB_USER=admin
DB_PASSWORD=${DB_PASSWORD}

# Redis Credentials
REDIS_PASSWORD=${REDIS_PASSWORD}
EOF
    echo -e "${GREEN}.env created successfully.${NC}"
    source .env
fi

# ------------------------------------------------------------------
# 2. Certificate Generation (Secrets Dir)
# ------------------------------------------------------------------
mkdir -p secrets

# SSL Keystore
if [ -f secrets/dev-keystore.p12 ]; then
    echo "secrets/dev-keystore.p12 already exists. Skipping."
else
    echo "Generating self-signed SSL certificate..."
    rm -f secrets/dev-keystore.p12
    
    keytool -genkeypair \
      -alias devcert \
      -keyalg EC \
      -keysize 256 \
      -validity 365 \
      -storetype PKCS12 \
      -keystore secrets/dev-keystore.p12 \
      -storepass changeit \
      -dname "CN=localhost, OU=Dev, O=SudoThis, L=Local, S=CO, C=US"
      
    echo -e "${GREEN}SSL Certificate created in secrets/dev-keystore.p12${NC}"
fi

# ------------------------------------------------------------------
# 3. Terraform Variables
# ------------------------------------------------------------------
TF_DIR="terraform/environments/local"
TF_VARS_FILE="${TF_DIR}/terraform.tfvars"

echo "Updating Terraform variables in ${TF_DIR}..."

cat <<EOF > ${TF_VARS_FILE}
db_user        = "${DB_USER}"
db_password    = "${DB_PASSWORD}"
redis_password = "${REDIS_PASSWORD}"
EOF

echo -e "${GREEN}Terraform variables updated at ${TF_VARS_FILE}${NC}"

# ------------------------------------------------------------------
# 4. Cleanup / Safety
# ------------------------------------------------------------------
chmod 600 .env
chmod 600 secrets/dev-keystore.p12
chmod 600 ${TF_VARS_FILE}

echo -e "${GREEN}Initialization Complete!${NC}"
echo "You can now run:"
echo "  1. ./mvnw quarkus:dev (starts app + dev services)"
echo "  2. cd terraform/environments/local && ./local_deploy.sh (deploys full stack)"
