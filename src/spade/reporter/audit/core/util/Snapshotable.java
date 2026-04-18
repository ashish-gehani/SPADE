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
package spade.reporter.audit.core.util;

/**
 * Implemented by types that can produce an immutable point-in-time copy of
 * themselves.
 *
 * @param <T> the concrete implementing type, so that {@link #snapshot()}
 *            returns the same type rather than a raw {@code Snapshotable}
 */
public interface Snapshotable<T extends Snapshotable<T>>{

	/**
	 * Returns an immutable snapshot of the current state.
	 * The returned instance must not reflect any mutations made to the
	 * original after this call.
	 *
	 * @return a point-in-time copy of this object
	 */
	public T snapshot();

}
