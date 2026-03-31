/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.core.event;

/**
 * Exception thrown when a {@link Factory} receives a {@link Context} of the
 * wrong type.
 *
 * Records the expected context type and the actual type (or {@code "(null)"}
 * when the context is {@code null}).
 */
public class InvalidContextException extends Exception{

	private static final long serialVersionUID = 1L;

	private final Class<? extends Context> expectedType;
	private final String actualType;

	public InvalidContextException(
		final Class<? extends Context> expectedType,
		final Context actual
	){
		super(String.format(
			"expectedType='%s' actualType='%s'",
			format(expectedType),
			format(actual)
		));
		this.expectedType = expectedType;
		this.actualType = format(actual);
	}

	public Class<? extends Context> getExpectedType(){
		return expectedType;
	}

	public String getActualType(){
		return actualType;
	}

	private static String format(final Class<? extends Context> type){
		return type == null ? "(null)" : type.getName();
	}

	private static String format(final Context context){
		return context == null ? "(null)" : context.getClass().getName();
	}

}
