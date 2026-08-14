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

public class Result<T>{

	public final boolean error;
	public final String errorMessage;
	public final Exception exception;
	public final T result;
	
	public final Result<?> cause;
	
	private Result(boolean error, String errorMessage, Exception exception, T result, Result<?> cause){
		this.error = error;
		this.errorMessage = errorMessage;
		this.exception = exception;
		this.result = result;
		this.cause = cause;
	}
	
	@Override
	public String toString(){
		return String.format("Result [%s=%s, %s=%s, %s=%s, %s=%s, %s=%s]", 
				"error", error, 
				"errorMessage", errorMessage,
				"exception", exception,
				"result", result,
				"cause", cause);
	}
	
	public String toErrorString(){
		if(error){
			String str = "";
			str += (errorMessage == null) ? "" : errorMessage +System.lineSeparator();
			str += (exception	 == null) ? "" : HelperFunctions.formatExceptionStackTrace(exception) + System.lineSeparator();
			str += (cause		 == null) ? "" : cause.toErrorString() + System.lineSeparator();
			return str;
		}else{
			return "No error";
		}
	}
	
	public static <T> Result<T> successful(T result){
		return new Result<T>(false, null, null, result, null);
	}
	
	public static <T> Result<T> failed(String errorMessage, Exception exception, Result<?> cause){
		return new Result<T>(true, errorMessage, exception, null, cause);
	}
	
	public static <T> Result<T> failed(String errorMessage){
		return failed(errorMessage, null, null);
	}
	
	public static <T> Result<T> failed(Exception exception){
		return failed(null, exception, null);
	}
	
	public static <T> Result<T> failed(String errorMessage, Result<?> cause){
		return failed(errorMessage, null, cause);
	}
	
	public static <T> Result<T> failed(Exception exception, Result<?> cause){
		return failed(null, exception, cause);
	}
}
