/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.storage.neo4j;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;
import spade.storage.Neo4j;
import spade.utility.HelperFunctions;

public class StorageTest{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	public final void test(final Neo4j storage){
		final long startMillis = System.currentTimeMillis();

		for(long l = 0; l < storage.getConfiguration().testVertexTotal; l++){
			storage.putVertex(testCreateVertex(storage.getConfiguration(), l));
		}

		long edgesCount = 0;

		for(long l = 0; l < storage.getConfiguration().testVertexTotal - 1; l++){
			for(long e = 0; e < storage.getConfiguration().testEdgeDegree; e++){
				storage.putEdge(testCreateEdge(storage.getConfiguration(), edgesCount++, l, l + 1));
			}
		}

		// tie a bow
		if(storage.getConfiguration().testVertexTotal > 0){ // at least 1
			for(long e = 0; e < storage.getConfiguration().testEdgeDegree; e++){
				storage.putEdge(testCreateEdge(storage.getConfiguration(), edgesCount++, storage.getConfiguration().testVertexTotal - 1, 0));
			}
		}

		HelperFunctions.sleepSafe(5 * 1000);

		while(storage.getPendingTasksSize() > 0){
			// Wait while stored everything
			HelperFunctions.sleepSafe(storage.getConfiguration().sleepWaitMillis);
		}
		//storage.shutdown();

		final long endMillis = System.currentTimeMillis();
		logger.log(Level.INFO, 
				storage.getClass().getSimpleName() + "Test: vertices=" 
				+ storage.getConfiguration().testVertexTotal + ", edges=" + edgesCount + ", seconds="
				+ ((double)(endMillis - startMillis) / (1000.000)) + "");
	}

	private final AbstractVertex testCreateVertex(final Configuration configuration, final long id){
		final Vertex vertex = new Vertex(testCreateVertexHash(id));
		vertex.addAnnotation("vertex_id", String.valueOf(id));
		vertex.addAnnotations(testGetResolvedAnnotations(id, configuration.getTestVertexAnnotationsListCopy()));
		return vertex;
	}

	private final String testCreateVertexHash(final long id){
		final String str = "vertex_hash_" + id;
		return DigestUtils.md5Hex(str);
	}

	private final AbstractEdge testCreateEdge(final Configuration configuration, final long edgeId, final long childId, final long parentId){
		final AbstractVertex child = testCreateVertex(configuration, childId);
		final AbstractVertex parent = testCreateVertex(configuration, parentId);
		final AbstractEdge edge = new Edge(testCreateEdgeHash(edgeId), child, parent);
		edge.addAnnotation("edge_id", String.valueOf(edgeId));
		edge.addAnnotations(testGetResolvedAnnotations(edgeId, configuration.getTestEdgeAnnotationsListCopy()));
		return edge;
	}

	private final String testCreateEdgeHash(final long id){
		final String str = "edge_hash_" + id;
		return DigestUtils.md5Hex(str);
	}

	private final Map<String, String> testGetResolvedAnnotations(final long id, final List<SimpleEntry<String, String>> list){
		final Map<String, String> map = new HashMap<String, String>();
		for(final SimpleEntry<String, String> entry : list){
			final String key = entry.getKey();
			final String value = entry.getValue();

			final String resolvedKey = key.replaceAll("\\$\\{id\\}", String.valueOf(id));
			final String resolvedValue = value.replaceAll("\\$\\{id\\}", String.valueOf(id));
			map.put(resolvedKey, resolvedValue);
		}
		return map;
	}
}
