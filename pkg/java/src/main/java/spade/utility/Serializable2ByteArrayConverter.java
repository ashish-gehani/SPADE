/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2019 SRI International

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
package spade.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Serializable2ByteArrayConverter<X extends Serializable> implements Converter<X, byte[]>{
	
	@Override
	public byte[] serialize(X i) throws Exception{
		if(i == null){
			return null;
		}else{
			ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
			objectOutputStream.writeObject(i);
			objectOutputStream.flush();
			return byteOutputStream.toByteArray();
		}
	}

	@Override
	public X deserialize(byte[] j) throws Exception{
		if(j == null){
			return null;
		}else{
			ByteArrayInputStream byteInputStream = new ByteArrayInputStream(j);
			ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
			return (X)objectInputStream.readObject();
		}
	}

	@Override
	public byte[] serializeObject(Object o) throws Exception{
		if(o == null){
			return null;
		}else{
			return serialize((X)o);
		}
	}

	@Override
	public X deserializeObject(Object o) throws Exception{
		if(o == null){
			return null;
		}else{
			return deserialize((byte[])o);
		}
	}

}
