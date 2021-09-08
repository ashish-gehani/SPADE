/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International
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

import java.util.HashMap;
import java.util.Map;

public class AggregationState{
	/*
	 * Previously computed stats can be kept here
	 */
	public static class VertexState
	{
		// map of (annotation name ->  histogram)
		private final Map<String, Map<String, Integer>> histograms = new HashMap<>();
		// map of (annotation name -> mean)
		private final Map<String, Double> means = new HashMap<>();
		// map of (annotation name -> std)
		private final Map<String, Double> stds = new HashMap<>();
	}

}
