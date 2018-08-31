#!/bin/bash

KEYS_PRESENT=cfg/keys/key.generation.done

if [ -f $KEYS_PRESENT ] ; then
	printf 'Keys present. Will not generate new ones.\n'
	exit
fi

mkdir -p cfg/keys/keystore
mkdir -p cfg/keys/private
mkdir -p cfg/keys/public

# Generate client public-private key pair into private keystore
echo Generating client public private key pair
keytool -genkey -alias clientprivate -keystore cfg/keys/private/client.private -storetype JKS -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private_password -keypass private_password

# Generate server public/private key pair
echo Generating server public private key pair
keytool -genkey -alias serverprivate -keystore cfg/keys/private/server.private -storetype JKS -keyalg rsa -dname "CN=Your Name, OU=Your Organizational Unit, O=Your Organization, L=Your City, S=Your State, C=Your Country" -storepass private_password -keypass private_password


# Export public keys from other hosts into public keystore
for file in cfg/keys/public/*.client.public; do
	filename=${file##*/}
	keytool -import -noprompt -alias "$filename" -keystore cfg/keys/public/client.public -file "$file" -storepass public_password
done
for file in cfg/keys/public/*.server.public; do
	filename=${file##*/}
	keytool -import -noprompt -alias "$filename" -keystore cfg/keys/public/server.public -file "$file" -storepass public_password
done


# Export client public key and import it into public keystore
echo Generating client public key file
keytool -export -alias clientprivate -keystore cfg/keys/private/client.private -file cfg/keys/temp.key -storepass private_password
keytool -import -noprompt -alias clientpublic -keystore cfg/keys/public/client.public -file cfg/keys/temp.key -storepass public_password
rm -f cfg/keys/temp.key

# Export server public key and import it into public keystore
echo Generating server public key file
keytool -export -alias serverprivate -keystore cfg/keys/private/server.private -file cfg/keys/temp.key -storepass private_password
keytool -import -noprompt -alias serverpublic -keystore cfg/keys/public/server.public -file cfg/keys/temp.key -storepass public_password
rm -f cfg/keys/temp.key

# Note that keys have been generated
touch $KEYS_PRESENT
