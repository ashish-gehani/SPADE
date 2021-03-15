#!/bin/bash

KEYS_PRESENT=cfg/keys/key.generation.done

if [ -f $KEYS_PRESENT ] ; then
	printf 'Keys present. Will not generate new ones.\nRun `addkeys.sh` to import public keys from remote hosts into the keystore.\n'
	exit
fi

mkdir -p cfg/keys/keystore
mkdir -p cfg/keys/private
mkdir -p cfg/keys/public

# Generate client public/private key pair into private keystore
echo Generating client public-private key pair
keytool -genkey -alias clientprivate -keystore cfg/keys/keystore/clientprivate.keystore -storetype PKCS12 -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private_password -keypass private_password

# Generate server public/private key pair
echo Generating server public-private key pair
keytool -genkey -alias serverprivate -keystore cfg/keys/keystore/serverprivate.keystore -storetype PKCS12 -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private_password -keypass private_password

# Export client public key and import it into public keystore
echo Generating client public key file
keytool -export -alias clientprivate -keystore cfg/keys/keystore/clientprivate.keystore -file cfg/keys/public/self.client.public -storepass private_password
keytool -import -noprompt -alias clientpublic -keystore cfg/keys/keystore/clientpublic.keystore -file cfg/keys/public/self.client.public -storepass public_password

# Export server public key and import it into public keystore
echo Generating server public key file
keytool -export -alias serverprivate -keystore cfg/keys/keystore/serverprivate.keystore -file cfg/keys/public/self.server.public -storepass private_password
keytool -import -noprompt -alias serverpublic -keystore cfg/keys/keystore/serverpublic.keystore -file cfg/keys/public/self.server.public -storepass public_password

# Note that keys have been generated
touch $KEYS_PRESENT
