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
package spade.transformer.query;

import java.util.Arrays;

import spade.transformer.query.parameter.LineageDirection;
import spade.transformer.query.parameter.MaxDepth;
import spade.transformer.query.parameter.SourceGraph;

/**
 * Extended Context class that contains all available parameters from spade.transformer.query.parameter.
 * This class provides convenient access to all parameter types used in transformer queries.
 */
public class ContextAllParams extends Context {

	public final SourceGraph sourceGraph = new SourceGraph();
	public final MaxDepth maxDepth = new MaxDepth();
	public final LineageDirection lineageDirection = new LineageDirection();

	/**
	 * Constructs a ContextAllParams with all available parameters initialized.
	 * The parameters are automatically added to the parent Context's parameter list.
	 */
	public ContextAllParams() {
		super();
		set(Arrays.asList(sourceGraph, maxDepth, lineageDirection));
	}
}
