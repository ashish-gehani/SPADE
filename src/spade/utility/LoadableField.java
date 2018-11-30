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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define this annotation for any Class level Field which is not 'static', and 'final'.
 * 
 * Then using the class 'LoadableFieldHelper' the field associated can be set using the properties
 * defined below. 
 * 
 * For supported Field types look at LoadableFieldHelper.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LoadableField{
	
	/**
	 * @return the name to refer to this field by
	 */
	String name();
	/**
	 * @return the meaning on null value for this field. If null and not optional then exception thrown.
	 */
	boolean optional();
	/**
	 * @return In case of array what to split the value by
	 */
	String splitBy() default "";
	/**
	 * @return In case of File whether to check if the path is file or directory. If true then checks if the path is a file.
	 */
	boolean mustBeDirectory() default false;
	/**
	 * @return the radix in case the type is either long or int
	 */
	int radix() default 10;
	/**
	 * @return the minimum value (inclusive) for double, long, int
	 */
	double min() default Double.NEGATIVE_INFINITY;
	/**
	 * @return the maximum value (inclusive) for double, long, int
	 */
	double max() default Double.POSITIVE_INFINITY;
	/**
	 * @return if the type is String and user wants to restrict the list of values allowed then define the allowed values in the array.
	 */
	String[] literalSet() default {};
		
}
