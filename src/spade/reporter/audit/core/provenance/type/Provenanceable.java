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
package spade.reporter.audit.core.provenance.type;

import java.util.Map;

/**
 * Implemented by types that can describe themselves as provenance annotations.
 *
 * Both methods must be implemented by the same class so that all annotations
 * for a given object are visible in one place, making it straightforward to
 * reason about what is being emitted.
 */
public interface Provenanceable{

	/**
	 * Returns the annotations that uniquely identify this object.
	 * These form the stable key by which the object is looked up or deduplicated
	 * in the provenance graph.
	 */
	public Map<String, String> getKeyAnnotations();

	/**
	 * Returns supplementary annotations that describe this object beyond its
	 * identity.  These enrich the provenance record but are not used as keys.
	 */
	public Map<String, String> getExtraAnnotations();

}
