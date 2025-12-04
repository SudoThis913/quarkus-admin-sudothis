keytool -genkeypair \
  -alias devcert \
  -keyalg EC \
  -keysize 256 \
  -validity 365 \
  -storetype PKCS12 \
  -keystore dev-keystore.p12 \
  -storepass changeit \
  -dname "CN=localhost, OU=Dev, O=SudoThis, L=Local, S=CO, C=US"