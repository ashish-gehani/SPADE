#!/bin/bash

mkdir -p ssl
rm -f ssl/*

# Generate client public/private key pair into private keystore
echo Generating client public private key pair
keytool -genkey -alias clientprivate -keystore ssl/client.private -storetype JKS -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private -keypass private

# Generate server public/private key pair
echo Generating server public private key pair
keytool -genkey -alias serverprivate -keystore ssl/server.private -storetype JKS -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private -keypass private

# Export client public key and import it into public keystore
echo Generating client public key file
keytool -export -alias clientprivate -keystore ssl/client.private -file ssl/temp.key -storepass private
keytool -import -noprompt -alias clientpublic -keystore ssl/client.public -file ssl/temp.key -storepass public
rm -f ssl/temp.key

# Export server public key and import it into public keystore
echo Generating server public key file
keytool -export -alias serverprivate -keystore ssl/server.private -file ssl/temp.key -storepass private
keytool -import -noprompt -alias serverpublic -keystore ssl/server.public -file ssl/temp.key -storepass public
rm -f ssl/temp.key
