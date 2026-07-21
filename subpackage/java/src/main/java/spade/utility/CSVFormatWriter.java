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
package spade.utility;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;

public class CSVFormatWriter implements AutoCloseable{
	private static final int defaultMaxFractionDigitis = 10;

	private final DecimalFormat decimalFormat = new DecimalFormat("#");
	private final int maxFractionDigits;
	private final BufferedWriter writer;

	public CSVFormatWriter(final OutputStream outputStream){
		this(outputStream, defaultMaxFractionDigitis);
	}

	private CSVFormatWriter(final OutputStream outputStream, final int maxFractionDigits){
		this.writer = new BufferedWriter(new OutputStreamWriter(outputStream));
		this.decimalFormat.setMaximumFractionDigits(maxFractionDigits);
		this.maxFractionDigits = maxFractionDigits;
	}

	public int getMaxFractionDigits(){
		return maxFractionDigits;
	}

	public void flush() throws Exception{
		this.writer.flush();
	}

	@Override
	public void close() throws Exception{
		this.writer.close();
	}

	public void writeLine(final Collection<?> values) throws Exception{
		String line = "";
		final Iterator<?> iterator = values.iterator(); 
		while(iterator.hasNext()){
			line += formatObject(iterator.next());
			if(iterator.hasNext()){
				line += ",";
			}
		}
		this.writer.write(line);
		newLine();
	}

	public void newLine() throws Exception{
		this.writer.newLine();
	}

	public String formatObject(final Object obj){
		return formatObject(decimalFormat, obj);
	}

	public static String formatObject(final DecimalFormat decimalFormat, final Object obj){
		if(obj == null){
			return "";
		}
		if(obj.getClass().isArray()){
			return formatArray((Object[])obj, decimalFormat);
		}
		if(obj instanceof String){
			return formatString((String)obj);
		}
		if(obj instanceof CharSequence){
			return formatCharSequence((CharSequence)obj);
		}
		if(obj instanceof Double || obj.getClass().equals(double.class)){
			return formatDouble((Double)obj, decimalFormat);
		}
		if(obj instanceof Integer || obj.getClass().equals(int.class)){
			return formatInteger((Integer)obj);
		}
		if(obj instanceof Long || obj.getClass().equals(long.class)){
			return formatLong((Long)obj);
		}
		if(obj instanceof Byte || obj.getClass().equals(byte.class)){
			return formatByte((Byte)obj);
		}
		if(obj instanceof Character || obj.getClass().equals(char.class)){
			return formatCharacter((Character)obj);
		}
		if(obj instanceof Boolean || obj.getClass().equals(boolean.class)){
			return formatBoolean((Boolean)obj);
		}
		if(obj instanceof Float || obj.getClass().equals(float.class)){
			return formatFloat((Float)obj, decimalFormat);
		}
		if(obj instanceof Short || obj.getClass().equals(short.class)){
			return formatShort((Short)obj);
		}
		if(obj instanceof Collection<?>){
			return formatCollection((Collection<?>)obj, decimalFormat);
		}
		return formatString(String.valueOf(obj));
	}

	private static String quote(final String str){
		return "\"" + str + "\"";
	}

	private static String escape(final String str){
		return str.replace("\"", "\"\"");
	}

	private static String formatString(final String str){
		final String escaped = escape(str);
		return quote(escaped);
	}

	private static String formatCharSequence(final CharSequence str){
		final String escaped = escape(str.toString());
		return quote(escaped);
	}

	private static String formatCharacter(final char value){
		final String escaped = escape(String.valueOf(value));
		return quote(escaped);
	}

	private static String formatByte(final byte value){
		return String.valueOf(value);
	}

	private static String formatDouble(final double value, final DecimalFormat decimalFormat){
		return decimalFormat.format(value);
	}

	private static String formatFloat(final float value, final DecimalFormat decimalFormat){
		return decimalFormat.format(value);
	}

	private static String formatBoolean(final boolean value){
		return String.valueOf(value);
	}

	private static String formatLong(final long value){
		return String.valueOf(value);
	}

	private static String formatInteger(final int value){
		return String.valueOf(value);
	}

	private static String formatShort(final short value){
		return String.valueOf(value);
	}

	private static String formatCollection(final Collection<?> collection, final DecimalFormat decimalFormat){
		return formatArray(collection.toArray(), decimalFormat);
	}

	private static String formatArray(final Object[] items, final DecimalFormat decimalFormat){
		String result = "";
		for(int i = 0; i < items.length; i++){
			final Object obj = items[i];
			final String formatted = formatObject(decimalFormat, obj);
			result += escape(formatted);
			if(i != items.length - 1){
				result += ",";
			}
		}
		result = quote(result);
		return result;
	}
}
