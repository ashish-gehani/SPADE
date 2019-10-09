#!/bin/bash

KEYS_PRESENT=cfg/ssl/key.generation.done

if [ -f $KEYS_PRESENT ] ; then
	printf 'Keys present. Will not generate new ones.\n'
	exit
fi

mkdir -p cfg/ssl

# Generate client public/private key pair into private keystore
echo Generating client public private key pair
keytool -genkey -alias clientprivate -keystore cfg/ssl/client.private -storetype PKCS12 -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private -keypass private

# Generate server public/private key pair
echo Generating server public private key pair
keytool -genkey -alias serverprivate -keystore cfg/ssl/server.private -storetype PKCS12 -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private -keypass private

# Export client public key and import it into public keystore
echo Generating client public key file
keytool -export -alias clientprivate -keystore cfg/ssl/client.private -file cfg/ssl/temp.key -storepass private
keytool -import -noprompt -alias clientpublic -keystore cfg/ssl/client.public -file cfg/ssl/temp.key -storepass public
rm -f cfg/ssl/temp.key

# Export server public key and import it into public keystore
echo Generating server public key file
keytool -export -alias serverprivate -keystore cfg/ssl/server.private -file cfg/ssl/temp.key -storepass private
keytool -import -noprompt -alias serverpublic -keystore cfg/ssl/server.public -file cfg/ssl/temp.key -storepass public
rm -f cfg/ssl/temp.key

# Note that keys have been generated
touch $KEYS_PRESENT
