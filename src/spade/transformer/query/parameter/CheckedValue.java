/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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
package spade.transformer.query.parameter;

import spade.utility.NotSetException;

/**
 * @param <T> the type of the value
 */
public class CheckedValue<T> {

	private T value;
	boolean isSet;

	/**
	 * Set the value directly.
	 *
	 * @param value the value to set
	 */
	public void setValue(T value) {
		this.value = value;
		this.isSet = true;
	}

	/**
	 * Gets the value.
	 *
	 * @return the parameter value
	 * @throws NotSetException if the value has not been set
	 */
	public T getValue() throws NotSetException {
		if (!isSet) {
			throw new NotSetException("Parameter has not been set");
		}
		return value;
	}

	/**
	 * Checks if the value has been set.
	 *
	 * @return true if the value has been set, false otherwise
	 */
	public boolean isSet() {
		return isSet;
	}
}
