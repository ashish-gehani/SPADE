#!/bin/bash

mkdir -p conf/ssl

# Generate client public/private key pair into private keystore
echo Generating client public private key pair
keytool -genkey -alias clientprivate -keystore conf/ssl/client.private -storetype JKS -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private -keypass private

# Generate server public/private key pair
echo Generating server public private key pair
keytool -genkey -alias serverprivate -keystore conf/ssl/server.private -storetype JKS -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private -keypass private

# Export client public key and import it into public keystore
echo Generating client public key file
keytool -export -alias clientprivate -keystore conf/ssl/client.private -file conf/ssl/temp.key -storepass private
keytool -import -noprompt -alias clientpublic -keystore conf/ssl/client.public -file conf/ssl/temp.key -storepass public
rm -f conf/ssl/temp.key

# Export server public key and import it into public keystore
echo Generating server public key file
keytool -export -alias serverprivate -keystore conf/ssl/server.private -file conf/ssl/temp.key -storepass private
keytool -import -noprompt -alias serverpublic -keystore conf/ssl/server.public -file conf/ssl/temp.key -storepass public
rm -f conf/ssl/temp.key
