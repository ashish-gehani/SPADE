/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.filter.clamprov;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class ClamProvLogReader implements AutoCloseable{

	private static final int 
		sizeOfUnsignedLong = 8,
		sizeOfInt = 4,
		sizeOfLong = 8,
		sizeOfFunctionName = 256,
		dataSize = (sizeOfUnsignedLong + sizeOfInt + (2 * sizeOfLong) + sizeOfFunctionName);

	public final String path;
	private DataInputStream dataInputStream;

	public ClamProvLogReader(final String path) throws Exception{
		final File file = new File(path);
		this.dataInputStream = new DataInputStream(new FileInputStream(file));

		this.path = path;
	}

	public ClamProvEvent read() throws Exception{
		final byte[] bytes = dataInputStream.readNBytes(dataSize);
		if(bytes.length == 0 || bytes.length < dataSize){
			return null;
		}
		return create(bytes);
	}

	private ClamProvEvent create(final byte[] bytes) throws Exception{
		final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		final long time = byteBuffer.getLong();
		final int pid = byteBuffer.getInt();
		final long callSiteId = byteBuffer.getLong();
		final long exit = byteBuffer.getLong();
		final byte[] functionNameBytes = new byte[byteBuffer.remaining()];
		byteBuffer.get(functionNameBytes);
		int stringEnd = 0;
		for(; stringEnd < functionNameBytes.length && functionNameBytes[stringEnd] != 0; stringEnd++){}
		final String functionNameString = new String(functionNameBytes, 0, stringEnd, Charset.defaultCharset());
		final ClamProvEvent event = new ClamProvEvent(time, pid, callSiteId, exit, functionNameString);
		return event;
	}

	@Override
	public void close() throws Exception{
		dataInputStream.close();
	}

}
