#!/bin/bash

mkdir -p cfg/keys/keystore
mkdir -p cfg/keys/public

# Export public keys from other hosts into public keystore
for file in cfg/keys/public/*.client.public; do
	[ -f "$file" ] || continue
	filename=${file##*/}
	keytool -import -noprompt -alias "$filename" -keystore cfg/keys/keystore/clientpublic.keystore -file "$file" -storepass public_password
done
for file in cfg/keys/public/*.server.public; do
	[ -f "$file" ] || continue
	filename=${file##*/}
	keytool -import -noprompt -alias "$filename" -keystore cfg/keys/keystore/serverpublic.keystore -file "$file" -storepass public_password
done

echo Added public keys from other hosts into the public keystore