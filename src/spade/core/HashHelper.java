/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

 This program is free software: you can redistribute it and/or  
 modify it under the terms of the GNU General Public License as  
 published by the Free Software Foundation, either version 3 of the  
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,  
 but WITHOUT ANY WARRANTY; without even the implied warranty of  
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU  
 General Public License for more details.

 You should have received a copy of the GNU General Public License  
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

/*
 * This is the class that should contains all the functions that can be required for
 * hashing of vertices and edges.
 */
public abstract class HashHelper{

	// Default hasher
	public final static HashHelper defaultInstance = new MD5HashHelper();
	
	public final String hashAlgorithmName;
	public final int bytesInHash;
	
	private HashHelper(final String hashAlgorithmName, final int bytesInHash){
		if(hashAlgorithmName == null){
			throw new RuntimeException("NULL hash algorithm name");
		}
		if(bytesInHash <= 0){
			throw new RuntimeException("Invalid hash algorithm bytes in hash");
		}
		this.hashAlgorithmName = hashAlgorithmName;
		this.bytesInHash = bytesInHash;
	}
	
	public abstract byte[] hashToByteArray(final String data);
	public abstract String hashToHexString(final String data);
	
	public final boolean isValidHashByteArray(final byte[] hash){
		if(hash != null){
			if(hash.length == bytesInHash){
				return true;
			}
		}
		return false;
	}
	
	public final boolean isValidHashHexString(final String hash){
		if(hash != null){
			if(hash.length() == (bytesInHash * 2)){
				for(int i = 0; i < hash.length(); i++){
					final char c = hash.charAt(i);
					switch(c){
						case '0':case '1':case '2':case '3':case '4':case '5':case '6':case '7':case '8':case '9':
						case 'a':case 'b':case 'c':case 'd':case 'e':case 'f':
						case 'A':case 'B':case 'C':case 'D':case 'E':case 'F':
						break;
						default: return false;
					}
				}
				return true;
			}
		}
		return false;
	}
	
	public final String convertHashByteArrayToHashHexString(final byte[] bytes){
		if(isValidHashByteArray(bytes)){
			try{
				return Hex.encodeHexString(bytes);
			}catch(Exception e){
				throw new RuntimeException("Failed to encode byte array: " + toList(bytes), e);
			}
		}else{
			throw new RuntimeException("Invalid hash bytes: " + toList(bytes));	
		}
	}
	
	public final byte[] convertHashHexStringToHashByteArray(final String hexString){
		if(isValidHashHexString(hexString)){
			try{
				return Hex.decodeHex(hexString.toCharArray());
			}catch(Exception e){
				throw new RuntimeException("Failed to decode hex string: " + hexString, e);
			}
		}else{
			throw new RuntimeException("Invalid hash hex string: " + hexString);
		}
	}
	
	@Override
	public final String toString(){
		return this.getClass().getSimpleName() + " [hashAlgorithmName=" + hashAlgorithmName + ", bytesInHash=" + bytesInHash + "]";
	}
	
	public static final class MD5HashHelper extends HashHelper{
		
		private MD5HashHelper(){
			super("md5", 16);
		}
		
		@Override
		public byte[] hashToByteArray(String data){
			return DigestUtils.md5(data);
		}

		@Override
		public String hashToHexString(String data){
			return DigestUtils.md5Hex(data);
		}

	}
	
	private static List<Byte> toList(final byte[] bytes){
		List<Byte> bytesList = new ArrayList<Byte>();
		for(byte b : bytes){
			bytesList.add(b);
		}
		return bytesList;
	}
	
	public static void main(String [] args) throws Exception{
		final String data = "test";
		final String hashHexString = defaultInstance.hashToHexString(data);
		final byte[] hashByteArray = defaultInstance.hashToByteArray(data);
		
		System.out.println(hashHexString + ", " + hashHexString.length());
		System.out.println(toList(hashByteArray) + ", " + hashByteArray.length);
		
		System.out.println(defaultInstance.convertHashByteArrayToHashHexString(hashByteArray));
		System.out.println(toList(defaultInstance.convertHashHexStringToHashByteArray(hashHexString)));
	}
}
