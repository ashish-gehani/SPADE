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
package spade.query.quickgrail.core;

import java.util.HashMap;
import java.util.Map;

import spade.utility.HelperFunctions;
import spade.utility.Result;

public class EnvironmentVariable{

	private static interface TypeParser<X>{
		public X parse(final String value);
	}
	
	private static final class IntegerParser implements TypeParser<Integer>{
		public final Integer parse(final String value){
			Result<Long> result = HelperFunctions.parseLong(value, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
			if(result.error){
				throw new RuntimeException("Invalid value '"+value+"'. Expected integer. " + result.toErrorString());
			}
			return result.result.intValue();
		}
	}
	
	/////////////////////////
	
	private static final IntegerParser typeParserInteger = new IntegerParser();
	
	private static final Map<Class<?>, TypeParser<?>> typeParsers = new HashMap<Class<?>, TypeParser<?>>();
	{
		typeParsers.put(Integer.class, typeParserInteger);
	}
	
	/////////////////////////
	
	public final String name;
	public final Class<?> type;
	private Object value;
	
	public EnvironmentVariable(final String name, final Class<?> type) throws IllegalArgumentException{
		if(HelperFunctions.isNullOrEmpty(name)){
			throw new IllegalArgumentException("NULL/Empty name: " + name);
		}
		if(type == null){
			throw new IllegalArgumentException("NULL type"); 
		}
		if(typeParsers.get(type) == null){
			throw new IllegalArgumentException("No parser defined for type: " + type);
		}
		this.name = name;
		this.type = type;
	}
	
	public final void setValue(final String value){
		this.value = typeParsers.get(type).parse(value);
	}
	
	public final void unsetValue(){
		this.value = null;
	}
	
	public final Object getValue(){
		return this.value;
	}
}
