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

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import spade.core.AbstractScreen;
import spade.screen.Deduplicate;
import spade.storage.Neo4j;

public class CacheManager{

	private final Object screenLock = new Object();
	private Deduplicate deduplicateScreen = null;
	
	private final Neo4j storage;
	
	public CacheManager(final Neo4j storage){
		this.storage = storage;
		
		synchronized(screenLock){
			final AbstractScreen screen = this.storage.findScreen(spade.screen.Deduplicate.class);
			if(screen != null){
				try{
					deduplicateScreen = (spade.screen.Deduplicate)screen;
				}catch(Throwable t){
					deduplicateScreen = null;
					throw new RuntimeException("Invalid screen returned instead of Deduplicate screen: " + screen.getClass(), t);
				}
				if(deduplicateScreen != null){
					if(storage.getConfiguration().reset){
						deduplicateScreen.reset();
					}
				}
			}
		}
	}
	
	public final Boolean executeTaskGetRelationshipByHashCode(final String hashCode, final Transaction tx){
		final TaskGetRelationshipByProperty storageTask = new TaskGetRelationshipByProperty(
				storage.getConfiguration().neo4jEdgeRelationshipType, storage.getConfiguration().hashPropertyName, hashCode);
		return storageTask.execute(storage, tx);
	}
	
	public final Boolean edgeCacheGet(final String hashCode, final Transaction tx){
		if(hashCode == null){
			return null;
		}

		Object value = null;
		synchronized(screenLock){
			if(deduplicateScreen != null){
				value = deduplicateScreen.getEdgeCacheValueForStorage(hashCode);
			}
		}

		if(value != null){
			try{
				return (Boolean)value;
			}catch(Exception e){
				throw new RuntimeException(
						"Invalid object ("+value+") type in cache. Expected '"+Boolean.class+"' but is '"+value.getClass()+"'", e);
			}
		}

		final Boolean found = executeTaskGetRelationshipByHashCode(hashCode, tx);

		if(found == null || found == false){
			return null;
		}

		edgeCachePut(hashCode, true);

		return true;
	}

	public final void edgeCachePut(final String hashCode, final boolean value){
		if(hashCode != null){
			synchronized(screenLock){
				if(deduplicateScreen != null){
					deduplicateScreen.setEdgeCacheValueForStorage(hashCode, value);
				}
			}
		}
	}

	public final void vertexCacheReset(){
		synchronized(screenLock){
			if(deduplicateScreen != null){
				deduplicateScreen.unsetAllVertexCacheValuesForStorage();
			}
		}
	}

	public final Node executeTaskGetNodeByHashCode(final String hashCode, final Transaction tx){
		final TaskGetNodeByProperty storageTask = new TaskGetNodeByProperty(
				storage.getConfiguration().neo4jVertexLabel, storage.getConfiguration().hashPropertyName, hashCode);
		return storageTask.execute(storage, tx);
	}

	public final Node vertexCacheGetNode(final String hashCode, final Transaction tx){
		if(hashCode == null){
			return null;
		}

		final Object value = vertexCacheGet(hashCode);
		if(value != null){
			try{
				return (Node)value;
			}catch(Exception e){
				throw new RuntimeException(
						"Invalid object ("+value+") type in cache. Expected '"+Node.class+"' but is '"+value.getClass()+"'", e);
			}
		}

		final Node node = executeTaskGetNodeByHashCode(hashCode, tx);

		if(node == null){
			return null;
		}

		vertexCachePutNode(hashCode, node);

		return node;
	}

	public final Long vertexCacheGetNodeId(final String hashCode, final Transaction tx){
		if(hashCode == null){
			return null;
		}

		final Object value = vertexCacheGet(hashCode);
		if(value != null){
			try{
				return (Long)value;
			}catch(Exception e){
				throw new RuntimeException(
						"Invalid object ("+value+") type in cache. Expected '"+Long.class+"' but is '"+value.getClass()+"'", e);
			}
		}

		final Node node = executeTaskGetNodeByHashCode(hashCode, tx);

		if(node == null){
			return null;
		}

		vertexCachePutNodeId(hashCode, node.getId());

		return node.getId();
	}

	public final Object vertexCacheGet(final String hashCode){
		if(hashCode != null){
			synchronized(screenLock){
				if(deduplicateScreen != null){
					return deduplicateScreen.getVertexCacheValueForStorage(hashCode);
				}
			}
		}
		return null;
	}

	public final void vertexCachePutNode(final String hashCode, final Node value){
		vertexCachePut(hashCode, value);
	}

	public final void vertexCachePutNodeId(final String hashCode, final long value){
		vertexCachePut(hashCode, value);
	}

	public final void vertexCachePut(final String hashCode, final Object value){
		if(hashCode != null){
			synchronized(screenLock){
				if(deduplicateScreen != null){
					deduplicateScreen.setVertexCacheValueForStorage(hashCode, value);
				}
			}
		}
	}
}
