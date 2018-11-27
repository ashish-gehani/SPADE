package spade.utility;

import java.io.File;
import java.lang.reflect.Field;

public abstract class VariableMetadata{

	public final String name;
	public final boolean optional;
	private final Object containingObject;
	private final Field field;
	
	public VariableMetadata(String name, boolean optional, Object containingObject, Field field){
		this.name = name;
		this.optional = optional;
		this.containingObject = containingObject;
		this.field = field;
	}
	
	public static void parse(VariableMetadata variable, String value){
		if(!variable.optional && value == null){
			throw new NullPointerException("NULL not allowed for: " + variable.name);
		}else{
			if(value != null){ // if null then no point in setting it
				variable.parse(value);
			}
		}
	}
	
	public static void assign(VariableMetadata variable) throws IllegalAccessException{
		Object value = variable.getAssignableObject();
		boolean isAccessible = variable.field.isAccessible();
		if(!isAccessible){
			variable.field.setAccessible(true);
		}
		variable.field.set(variable.containingObject, value);
		if(!isAccessible){
			variable.field.setAccessible(false);
		}
	}
	
	public static String getAsPrintableString(String key, VariableMetadata variable){
		return String.format("'%s'='%s'", key, variable.getParsedValue());
	}
	
	public abstract void parse(String value);
	public abstract Object getAssignableObject();
	public abstract Object getParsedValue();
	
//	public static class CommaSeparatedValues extends VariableMetadata{
//		private List<VariableMetadata> variables = TODO
//	}
	
	public static class StringVariable extends VariableMetadata{
		private String value;
		public StringVariable(String name, boolean optional, Object containingObject, Field field){
			super(name, optional, containingObject, field);
		}
		public void parse(String value){
			this.value = value;
		}
		public Object getAssignableObject(){
			Field field = super.field;
			Class<?> clazz = field.getType();
			if(clazz.equals(String.class)){
				return value;
			}else if(clazz.equals(StringBuffer.class)){
				if(value != null){
					return new StringBuffer(value);
				}else{
					return null;
				}
			}else if(clazz.equals(StringBuilder.class)){
				if(value != null){
					return new StringBuilder(value);
				}else{
					return null;
				}
			}else{
				throw new IllegalArgumentException("Type mismatch between '"+clazz+"' and '"+this.getClass()+"' for: " + name);
			}
		}
		public Object getParsedValue(){
			return value;
		}
	}
	
	public static class PathVariable extends VariableMetadata{
		private File file;
		private boolean mustBeFile;
		// if opposite of file then must be a dir
		public PathVariable(String name, boolean optional, Object containingObject, Field field, boolean mustBeFile){
			super(name, optional, containingObject, field);
			this.mustBeFile = mustBeFile;
		}
		public void parse(String value){
			file = new File(value);
			if(mustBeFile){
				if(!file.isFile()){
					throw new IllegalArgumentException("'"+value+"' must be a file for: " + name);
				}
			}else{
				if(!file.isDirectory()){
					throw new IllegalArgumentException("'"+value+"' must be a directory for: " + name);
				}
			}
		}
		public Object getAssignableObject(){
			Field field = super.field;
			Class<?> clazz = field.getType();
			if(clazz.equals(String.class)){
				if(file == null){
					return null;
				}else{
					return file.getAbsolutePath();
				}
			}else if(clazz.equals(File.class)){
				return file;
			}else{
				throw new IllegalArgumentException("Type mismatch between '"+clazz+"' and '"+this.getClass()+"' for: " + name);
			}
		}
		public Object getParsedValue(){
			return file == null ? null : file.getAbsolutePath();
		}
	}
	
	public static class BooleanVariable extends VariableMetadata{
		private Boolean value;
		public BooleanVariable(String name, boolean optional, Object containingObject, Field field){
			super(name, optional, containingObject, field);
		}
		public void parse(String value){
			String updatedValue = value.toLowerCase();
			switch(updatedValue){
				case "on":
				case "1":
				case "true":
				case "yes":
					this.value = true; break;
				case "off":
				case "0":
				case "false":
				case "no":
					this.value = false; break;
				default:
					throw new IllegalArgumentException("'"+value+"' not a boolean value for: " + name);
			}
		}
		public Object getAssignableObject(){
			Field field = super.field;
			Class<?> clazz = field.getType();
			if(clazz.equals(Boolean.class) || clazz.equals(boolean.class)){
				return value;
			}else{
				throw new IllegalArgumentException("Type mismatch between '"+clazz+"' and '"+this.getClass()+"' for: " + name);
			}
		}
		public Object getParsedValue(){
			return value;
		}
	}
	
	public static class LongVariable extends VariableMetadata{
		private final long min, max;
		private final int radix;
		private Long value;
		public LongVariable(String name, boolean optional, Object containingObject, Field field, int radix, long min, long max){
			super(name, optional, containingObject, field);
			this.radix = radix;
			this.min = min;
			this.max = max;
		}
		public void parse(String value){
			try{
				Long l = Long.parseLong(value, radix);
				if(l < min || l > max){
					throw new IllegalArgumentException("'"+l+"' not in range '["+ min + "," + max +"]' for: " + name);
				}
				this.value = l;
			}catch(Exception e){
				throw e;
			}
		}
		public Object getAssignableObject(){
			Field field = super.field;
			Class<?> clazz = field.getType();
			if(clazz.equals(Long.class) || clazz.equals(long.class)){
				return value;
			}else if(clazz.equals(Integer.class) || clazz.equals(int.class)){
				if(value == null){
					return null;
				}else{
					return value.intValue();
				}
			}else{
				throw new IllegalArgumentException("Type mismatch between '"+clazz+"' and '"+this.getClass()+"' for: " + name);
			}
		}
		public Object getParsedValue(){
			return value;
		}
	}
	
	public static class DoubleVariable extends VariableMetadata{
		private final double min, max; // inclusive
		private Double value;
		public DoubleVariable(String name, boolean optional, Object containingObject, Field field, double min, double max){
			super(name, optional, containingObject, field);
			this.min = min;
			this.max = max;
		}
		public void parse(String value){
			try{
				Double d = Double.parseDouble(value);
				if(d < min || d > max){
					throw new IllegalArgumentException("'"+d+"' not in range '["+ min + "," + max +"]' for: " + name);
				}
				this.value = d;
			}catch(Exception e){
				throw e;
			}
		}
		public Object getAssignableObject(){
			Field field = super.field;
			Class<?> clazz = field.getType();
			if(clazz.equals(Double.class) || clazz.equals(double.class)){
				return value;
			}else{
				throw new IllegalArgumentException("Type mismatch between '"+clazz+"' and '"+this.getClass()+"' for: " + name);
			}
		}
		public Object getParsedValue(){
			return value;
		}
	}
}
