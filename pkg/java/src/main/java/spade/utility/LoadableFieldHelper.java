/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A class to help process LoadableField fields in given objects
 */
public class LoadableFieldHelper{
	
	//////////////////////////////////////////////////////////////////
	
	/**
	 * All available parsers at the moment
	 */
	private static final Parser<?> stringParser = new StringParser(),
			doubleParser = new DoubleParser(),
			longParser = new LongParser(),
			integerParser = new IntegerParser(),
			booleanParser = new BooleanParser(),
			pathParser = new PathParser();

	/**
	 * Map from class to actual parser for easy lookup
	 */
	private static final Map<Class<?>, Parser<?>> parsers = new HashMap<Class<?>, Parser<?>>(){
		private static final long serialVersionUID = -4345368932368201230L;
		{
			put(String.class, stringParser);
			put(Double.class, doubleParser); put(double.class, doubleParser);
			put(Long.class, longParser); put(long.class, longParser);
			put(Integer.class, integerParser); put(int.class, integerParser);
			put(Boolean.class, booleanParser); put(boolean.class, booleanParser);
			put(File.class, pathParser);
		}
	};
	
	//////////////////////////////////////////////////////////////////

	/**
	 * Calls the appropriate parser based on the type of the field. Exception if no appropriate parser.
	 * 
	 * If failed to parse then exception added to ParseResult otherwise result set in the ParseResult object.
	 * 
	 * @param fieldType type of the field like int, boolean, and etc
	 * @param name name of the field as specified inside the annotation
	 * @param value value to parse (non-null)
	 * @param loadableFieldAnnotation the instance of the annotation as specified on the field
	 * @return ParseResult containing the result or the error that occurred
	 */
	private static ParseResult parseSingleField(Class<?> fieldType, String name, String value, LoadableField loadableFieldAnnotation){
		ParseResult result = null;
		Parser<?> parser = parsers.get(fieldType);
		if(parser != null){
			result = parser.parse(name, value, loadableFieldAnnotation);
		}else{
			result = new ParseResult(String.format("'%s' type not supported for '%s'. Supported type:%s", parsers.keySet()), null);
		}
		return result;
	}
	
	/**
	 * Splits the value by the token in the annotation
	 * 
	 * Calls parseSingleField on each token
	 * 
	 * The result in the ParseResult is an array of the type fieldType.
	 * 
	 * @param fieldType type of array i.e. int, boolean, and etc
	 * @param name name of the field as specified in the annotation
	 * @param value value to split and parse (non-null)
	 * @param loadableFieldAnnotation the instance of the annotation
	 * @return ParseResult containing the error or the result
	 * @throws LoadableFieldParseException
	 */
	private static ParseResult parseArrayField(Class<?> fieldType, String name, String value, LoadableField loadableFieldAnnotation)
			throws LoadableFieldParseException{
		Class<?> componentType = fieldType.getComponentType();
		String splitBy = loadableFieldAnnotation.splitBy();
		String tokens[] = value.split(splitBy, Integer.MAX_VALUE);
		int tokensLength = tokens.length; // Will always be at least 1.
		Object[] array = (Object[])Array.newInstance(componentType, tokensLength);
		for(int i = 0; i < tokensLength; i++){
			String token = tokens[i];
			ParseResult result = parseSingleField(componentType, name, token, loadableFieldAnnotation);
			if(!result.error){
				array[i] = result.result;
			}else{
				return result;
			}
		}
		return new ParseResult(array);
	}
	
	/**
	 * Sets the given value for the field inside the given object
	 * 
	 * Throws all exceptions that can be thrown by Field.set
	 * 
	 * Changes the accessibility of the field if it is false
	 * 
	 * @param object the instance of the class to which the field belongs
	 * @param field the field to set the value of
	 * @param value the value to set
	 * @throws Exception
	 */
	private static void setField(Object object, Field field, Object value) throws Exception{
		boolean changedAccessible = false;
		boolean isAccessible = field.isAccessible();
		if(!isAccessible){
			changedAccessible = true;
			field.setAccessible(true);
		}
		field.set(object, value);
		if(changedAccessible){
			field.setAccessible(false);
		}
	}
	
	/**
	 * Experimental *****
	 * 
	 * @param object
	 * @param field
	 * @return
	 * @throws Exception
	 */
	private static Object getFieldValue(Object object, Field field) throws Exception{
		Object value = null;
		boolean changedAccessible = false;
		boolean isAccessible = field.isAccessible();
		if(!isAccessible){
			changedAccessible = true;
			field.setAccessible(true);
		}
		value = field.get(object);
		if(changedAccessible){
			field.setAccessible(false);
		}
		return value;
	}
	
	/**
	 * Returns all the fields in the given class which have 'the' annotation
	 * 
	 * @param clazz the class to look into
	 * @return NEVER null. Either empty (if none found) or a set of fields
	 */
	public static Set<Field> getAllLoadableFields(Class<?> clazz){
		Set<Field> loadableFields = new HashSet<Field>();
		Field[] declaredFields = clazz.getDeclaredFields();
		for(Field declaredField : declaredFields){
			LoadableField loadableFieldAnnotation = declaredField.getDeclaredAnnotation(LoadableField.class);
			if(loadableFieldAnnotation != null){
				loadableFields.add(declaredField);
			}
		}
		return loadableFields;
	}
	
	/**
	 * Returns all the fields in the given class which have 'the' annotation with the name in the given set
	 * 
	 * @param clazz the class to look into
	 * @param names set of names of annotations on the fields. see annotation definition
	 * @return NEVER null. Either empty (if none found) or a set of fields
	 */
	public static Set<Field> getLoadableFieldsWithNamesInSet(Class<?> clazz, Set<String> names){
		Set<Field> loadableFields = new HashSet<Field>();
		Field[] declaredFields = clazz.getDeclaredFields();
		for(Field declaredField : declaredFields){
			LoadableField loadableFieldAnnotation = declaredField.getDeclaredAnnotation(LoadableField.class);
			if(loadableFieldAnnotation != null){
				String name = loadableFieldAnnotation.name();
				if(names.contains(name)){
					loadableFields.add(declaredField);
				}
			}
		}
		return loadableFields;
	}
	
	/**
	 * Sets the value for the passed field.
	 * 
	 * Checks whether the field provided is a legal field is not. All fields except with 
	 * 'static' & 'final' modifiers are valid.
	 * If 'isValuePresent' is false, and the field is optional then does nothing.
	 * If value is null, and field is optional then sets null.
	 * Parse field to get result.
	 * If failed then throw exception.
	 * If success in parsing then set field.
	 * If failed in setting field then throw exception.
	 * 
	 * @param object instance to which the field belongs
	 * @param field field to set
	 * @param value value to parse
	 * @param isValuePresent if value was provided from the source (like map, config, and etc)
	 * @param loadableFieldAnnotation the instance of annotation with the appropriate values filled
	 * @throws LoadableFieldIllegalAssignmentException
	 * @throws LoadableFieldFieldSetException
	 * @throws LoadableFieldParseException
	 * @throws NullPointerException
	 */
	private static void loadField(Object object, Field field, String value, boolean isValuePresent, LoadableField loadableFieldAnnotation) 
				throws LoadableFieldIllegalAssignmentException, LoadableFieldFieldSetException, 
				LoadableFieldParseException, NullPointerException{
		Class<?> clazz = object.getClass();
		String name = loadableFieldAnnotation.name();
		int modifiers = field.getModifiers();
		if(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)){
			throw new LoadableFieldIllegalAssignmentException(clazz, field.getName(), name);
		}else{
			Class<?> fieldType = field.getType();
			ParseResult result = null;
			if(!isValuePresent && loadableFieldAnnotation.optional()){
				result = null;
				// Don't set the value if not in map, is null, and is optional.
				// If not done then might overwrite valid values with null.
			}else{
				if(value == null && loadableFieldAnnotation.optional()){
					// Do overwrite with null if it was present in map
					result = new ParseResult(null);
				}else{
					try{
						if(fieldType.isArray()){
							result = parseArrayField(fieldType, name, value, loadableFieldAnnotation);
						}else{
							result = parseSingleField(fieldType, name, value, loadableFieldAnnotation);
						}
					}catch(Exception e){
						result = new ParseResult(String.format("Failed to parse '%s' for name '%s' with field name '%s'", 
								value, name, clazz.getName()), e);
					}
				}
			}
			
			if(result != null){
				if(result.error){
					throw new LoadableFieldParseException(clazz, result);
				}else{
					try{
						setField(object, field, result.result);
					}catch(Exception e){
						throw new LoadableFieldFieldSetException(clazz, field.getName(), name, result.result, e);
					}
				}
			}
		}
	}
	
	/**
	 * Call this function if you want to set all the annotated fields in the given object.
	 * 
	 * The keyValues map must contain all the names of the mandatory fields annotated in the given object.
	 * 
	 * Traverses all the fields in the given object.
	 * Collects all the annotated fields.
	 * For each annotated field gets the value from the map (using the name specified in the annotation)
	 * Parses the field
	 * Sets the field
	 * 
	 * If any error occurs then exception is thrown. If no exception then either success or no value was
	 * there to set.
	 * 
	 * @param object The object which contains the annotated fields
	 * @param keyValues map that contains the keys (name in the annotation, and not the name of the field) and the values to parse
	 * @throws LoadableFieldIllegalAssignmentException
	 * @throws LoadableFieldFieldSetException
	 * @throws LoadableFieldParseException
	 */
	public static void loadAllLoadableFieldsFromMap(Object object, Map<String, String> keyValues) throws LoadableFieldIllegalAssignmentException, 
													LoadableFieldFieldSetException, LoadableFieldParseException, LoadableFieldsNotSpecifiedForKeys{
		Set<String> loadableFieldNames = new HashSet<String>();
		Class<?> enclosingClass = object.getClass();
		Set<Field> loadableFields = getAllLoadableFields(enclosingClass);
		if(loadableFields != null){
			for(Field loadableField : loadableFields){
				LoadableField loadableFieldAnnotation = loadableField.getDeclaredAnnotation(LoadableField.class);
				String name = loadableFieldAnnotation.name();
				loadableFieldNames.add(name);
				boolean isValuePresent = keyValues.containsKey(name);
				String value = keyValues.get(name);
				loadField(object, loadableField, value, isValuePresent, loadableFieldAnnotation);
			}
		}
		if(!loadableFieldNames.containsAll(keyValues.keySet())){
			Set<String> unspecifiedKeys = new HashSet<String>(keyValues.keySet());
			unspecifiedKeys.removeAll(loadableFieldNames);
			throw new LoadableFieldsNotSpecifiedForKeys(enclosingClass, unspecifiedKeys);
		}
	}
	
	public static String allLoadableFieldsToString(Object object) throws Exception{
		StringBuffer string = new StringBuffer();
		Class<?> enclosingClass = object.getClass();
		Set<Field> loadableFields = getAllLoadableFields(enclosingClass);
		if(loadableFields != null){
			for(Field loadableField : loadableFields){
				LoadableField loadableFieldAnnotation = loadableField.getDeclaredAnnotation(LoadableField.class);
				String name = loadableFieldAnnotation.name();
				Object value = getFieldValue(object, loadableField);
				if(value == null){
					string.append(name).append("=").append("(null)").append(" ");
				}else{
					if(loadableField.getType().isArray()){
						string.append(name).append("=").append(Arrays.toString((Object[])value)).append(" ");
					}else{
						string.append(name).append("=").append(value).append(" ");
					}
				}
			}
		}
		if(string.length() > 0){
			string = string.deleteCharAt(string.length() - 1);
		}
		return string.toString();
	}
	
	//////////////////////////////////////////////////////////////////
	
	/**
	 * Implement this interface if you want the generic type referenced to be parsed from String to 
	 * referenced type 'T'.
	 * 
	 * @param <T> type to convert String to
	 */
	private static interface Parser<T>{
		/**
		 * Must always return a non-null ParseResult instance. Never throw an exception ideally.
		 * Catch the exception and wrap them in the ParseResult object.
		 * 
		 * @param name the name specified in the annotation
		 * @param value to be parsed
		 * @param loadableFieldAnnotation the instance on field for which the value is being parsed
		 * @return
		 */
		public ParseResult parse(String name, String value, LoadableField loadableFieldAnnotation);
	}
	
	private static class StringParser implements Parser<String>{
		@Override
		public ParseResult parse(String name, String value, LoadableField loadableFieldAnnotation){
			Set<String> literalSet = new HashSet<String>(Arrays.asList(loadableFieldAnnotation.literalSet()));
			if(!literalSet.isEmpty()){
				if(literalSet.contains(value)){
					return new ParseResult(value);
				}else{
					return new ParseResult(String.format("Value '%s' for '%s' not in %s", value, name, literalSet), null);
				}
			}else{
				return new ParseResult(value);
			}
		}
	}
	
	private static class DoubleParser implements Parser<Double>{
		@Override
		public ParseResult parse(String name, String value, LoadableField loadableFieldAnnotation){
			try{
				Double d = Double.parseDouble(value);
				if(d < loadableFieldAnnotation.min() || d > loadableFieldAnnotation.max()){
					return new ParseResult(String.format("Value '%s' not in range for '%s'. Range:[%s,%s]", d, name, 
							loadableFieldAnnotation.min(), loadableFieldAnnotation.max()), null);
				}else{
					return new ParseResult(d);
				}
			}catch(Exception e){
				return new ParseResult(String.format("Value '%s' not parseable for '%s'. Range:[%s,%s]", value, name, 
						loadableFieldAnnotation.min(), loadableFieldAnnotation.max()), e);
			}
		}
	}
	
	private static class LongParser implements Parser<Long>{
		@Override
		public ParseResult parse(String name, String value, LoadableField loadableFieldAnnotation){
			try{
				Long l = Long.parseLong(value, loadableFieldAnnotation.radix());
				if(l < loadableFieldAnnotation.min() || l > loadableFieldAnnotation.max()){
					return new ParseResult(String.format("Value '%s' not in range for '%s'. Range:[%s,%s], Radix:%s", 
							l, name, loadableFieldAnnotation.min(), loadableFieldAnnotation.max(), loadableFieldAnnotation.radix()), null);
				}else{
					return new ParseResult(l);
				}
			}catch(Exception e){
				return new ParseResult(String.format("Value '%s' not parseable for '%s'. Range:[%s,%s], Radix:%s", 
						value, name, loadableFieldAnnotation.min(), loadableFieldAnnotation.max(), loadableFieldAnnotation.radix()), e);
			}
		}
	}
	
	private static class IntegerParser implements Parser<Integer>{
		@Override
		public ParseResult parse(String name, String value, LoadableField loadableFieldAnnotation){
			try{
				Integer i = Integer.parseInt(value, loadableFieldAnnotation.radix());
				if(i < loadableFieldAnnotation.min() || i > loadableFieldAnnotation.max()){
					return new ParseResult(String.format("Value '%s' not in range for '%s'. Range:[%s,%s], Radix:%s", 
							i, name, loadableFieldAnnotation.min(), loadableFieldAnnotation.max(), loadableFieldAnnotation.radix()), null);
				}else{
					return new ParseResult(i);
				}
			}catch(Exception e){
				return new ParseResult(String.format("Value '%s' not parseable for '%s'. Range:[%s,%s], Radix:%s", 
						value, name, loadableFieldAnnotation.min(), loadableFieldAnnotation.max(), loadableFieldAnnotation.radix()), e);
			}
		}
	}
	
	private static class BooleanParser implements Parser<Boolean>{
		@Override
		public ParseResult parse(String name, String value, LoadableField loadableFieldAnnotation){
			String lowereCaseValue = value.toLowerCase();
			switch(lowereCaseValue){
				case "on": case "yes": case "1": case "true": return new ParseResult(true);
				case "no": case "off": case "0": case "false": return new ParseResult(false);
				default: return new ParseResult(String.format("Value '%s' for '%s' not recognized as boolean", value, name), null);
			}
		}
	}
	
	private static class PathParser implements Parser<File>{
		@Override
		public ParseResult parse(String name, String value, LoadableField loadableFieldAnnotation){
			try{
				File file = new File(value);
				try{
					if(!loadableFieldAnnotation.mustBeDirectory()){
						if(file.isFile()){
							return new ParseResult(file);
						}else{
							return new ParseResult(String.format("'%s' not a file or doesn't exist for '%s'", value, name), null);
						}
					}else{
						if(file.isDirectory()){
							return new ParseResult(file);
						}else{
							return new ParseResult(String.format("'%s' not a directory or doesn't exist for '%s'", value, name), null);
						}
					}
				}catch(Exception e){
					return new ParseResult(String.format("Failed to do file/directory check for value '%s' with name '%s'", value, name), e);
				}
			}catch(Exception e){
				return new ParseResult(String.format("Invalid path value '%s' for '%s'", value, name), e);
			}
		}
	}
	
	//////////////////////////////////////////////////////////////////

	/**
	 * Result of parsing the value. Initialized by any of the parsers.
	 * If error == true then use errorMessage and exception fields
	 * If error == false then use result field
	 */
	private static class ParseResult{
		/**
		 * Auto set based on which constructor is called
		 */
		public final boolean error;
		/**
		 * Appropriate message as constructed by the parser
		 */
		public final String errorMessage;
		/**
		 * Any exception that occurred
		 */
		public final Exception exception;
		/**
		 * Result of the parser
		 */
		public final Object result;
		/**
		 * Use in case of successfully parsing the value
		 * @param result
		 */
		private ParseResult(Object result){
			this.error = false;
			this.result = result;
			this.errorMessage = null;
			this.exception = null;
		}
		/**
		 * Use in case of failing to parse the value
		 * @param errorMessage The message to print
		 * @param exception if any occurs. Otherwise null
		 */
		private ParseResult(String errorMessage, Exception exception){
			this.error = true;
			this.errorMessage = errorMessage;
			this.exception = exception;
			this.result = null;
		}
	}
	
	/**
	 * Thrown when unspecified key(s) seen to set the value of a loadable field
	 */
	public static class LoadableFieldsNotSpecifiedForKeys extends Exception{
		private static final long serialVersionUID = -6036715092626261693L;
		/**
		 * @param enclosingClass Class that contains the field for which the value was being parsed
		 * @param unspecifiedKeys keys that were asked to be parsed but no loadable field matched for them
		 */
		private LoadableFieldsNotSpecifiedForKeys(Class<?> enclosingClass, Set<String> unspecifiedKeys){
			super(String.format("No LoadableField in enclosing class '%s' with name(s) %s (passed in arguments)", enclosingClass.getName(), unspecifiedKeys));
		}
	}
	
	/**
	 * Thrown when parsing of the value for a field fails
	 */
	public static class LoadableFieldParseException extends Exception{
		private static final long serialVersionUID = 1552162983058231466L;
		/**
		 * @param enclosingClass Class that contains the field for which the value was being parsed
		 * @param result The result value from the parser
		 */
		private LoadableFieldParseException(Class<?> enclosingClass, ParseResult result){
			super(String.format("%s. Enclosing class '%s'", result.errorMessage, enclosingClass.getName()), result.exception);
		}
	}
	
	/**
	 * Thrown when the modifiers of the field on which the annotation was assigned are 'static' & 'final'
	 */
	public static class LoadableFieldIllegalAssignmentException extends Exception{
		private static final long serialVersionUID = 8024656707167072790L;
		/**
		 * @param enclosingClass Class that contains the field which has illegal modifiers
		 * @param fieldName name of the field i.e. the variable in the class which had illegal modifiers
		 * @param loadableName name used in the annotation on the field
		 */
		private LoadableFieldIllegalAssignmentException(Class<?> enclosingClass, String fieldName, String loadableName){
			super(String.format("Illegal modifiers for field '%s' with name '%s' in enclosing class '%s'. Must not be 'static' & 'final'", 
					fieldName, loadableName, enclosingClass.getName()));
		}
	}
	
	/**
	 * Thrown when Field.set fails
	 * Contains the 'cause' exception when a field 'set' fails
	 */
	public static class LoadableFieldFieldSetException extends Exception{
		private static final long serialVersionUID = -4395406140526764409L;
		/**
		 * @param enclosingClass Class that contains the field which wasn't set
		 * @param fieldName name of the field i.e. the variable in the class on which the 'set' failed
		 * @param loadableName name used in the annotation on the field
		 * @param value the value which was to be assigned to the field
		 * @param e exception thrown by Field.set
		 */
		private LoadableFieldFieldSetException(Class<?> enclosingClass, String fieldName, String loadableName, Object value, Exception e){
			super(String.format("Failed to set value '%s' for field '%s' with name '%s' in enclosing class '%s'", 
					value, fieldName, loadableName, enclosingClass.getName()), e);
		}
	}
}
