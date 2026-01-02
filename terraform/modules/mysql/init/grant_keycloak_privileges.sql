CREATE DATABASE IF NOT EXISTS keycloak;
-- Ensure user exists and force native password plugin for compatibility
CREATE USER IF NOT EXISTS 'admin'@'%' IDENTIFIED WITH mysql_native_password BY 'admin123';
ALTER USER 'admin'@'%' IDENTIFIED WITH mysql_native_password BY 'admin123';
GRANT ALL PRIVILEGES ON keycloak.* TO 'admin'@'%';
FLUSH PRIVILEGES;