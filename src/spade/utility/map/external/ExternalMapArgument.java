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
package spade.utility.map.external;

import spade.utility.map.external.cache.CacheArgument;
import spade.utility.map.external.screen.ScreenArgument;
import spade.utility.map.external.store.StoreArgument;

public class ExternalMapArgument{

	public static final String keyMapArgument = "argument",
								keyMapReportingSeconds = "reportingSeconds",
								keyMapFlushOnClose = "flushOnClose",
								keyScreenName = "screenName",
								keyScreenArgument = "screenArgument",
								keyCacheName = "cacheName",
								keyCacheArgument = "cacheArgument",
								keyStoreName = "storeName",
								keyStoreArgument = "storeArgument";
	
	public final String mapId;
	
	public final ScreenArgument screenArgument;
	public final CacheArgument cacheArgument;
	public final StoreArgument storeArgument;
	
	public final Long reportingIntervalMillis;
	public final boolean flushCacheOnClose;
	
	protected ExternalMapArgument(String mapId, 
			ScreenArgument screenArgument, CacheArgument cacheArgument, StoreArgument storeArgument,
			Long reportingIntervalMillis, boolean flushCacheOnClose){
		this.mapId = mapId;
		this.screenArgument = screenArgument;
		this.cacheArgument = cacheArgument;
		this.storeArgument = storeArgument;
		this.reportingIntervalMillis = reportingIntervalMillis;
		this.flushCacheOnClose = flushCacheOnClose;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cacheArgument == null) ? 0 : cacheArgument.hashCode());
		result = prime * result + (flushCacheOnClose ? 1231 : 1237);
		result = prime * result + ((mapId == null) ? 0 : mapId.hashCode());
		result = prime * result + ((reportingIntervalMillis == null) ? 0 : reportingIntervalMillis.hashCode());
		result = prime * result + ((screenArgument == null) ? 0 : screenArgument.hashCode());
		result = prime * result + ((storeArgument == null) ? 0 : storeArgument.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		ExternalMapArgument other = (ExternalMapArgument) obj;
		if(cacheArgument == null){
			if(other.cacheArgument != null)
				return false;
		}else if(!cacheArgument.equals(other.cacheArgument))
			return false;
		if(flushCacheOnClose != other.flushCacheOnClose)
			return false;
		if(mapId == null){
			if(other.mapId != null)
				return false;
		}else if(!mapId.equals(other.mapId))
			return false;
		if(reportingIntervalMillis == null){
			if(other.reportingIntervalMillis != null)
				return false;
		}else if(!reportingIntervalMillis.equals(other.reportingIntervalMillis))
			return false;
		if(screenArgument == null){
			if(other.screenArgument != null)
				return false;
		}else if(!screenArgument.equals(other.screenArgument))
			return false;
		if(storeArgument == null){
			if(other.storeArgument != null)
				return false;
		}else if(!storeArgument.equals(other.storeArgument))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "ExternalMapArgument [mapId=" + mapId + ", screenArgument=" + screenArgument + ", cacheArgument="
				+ cacheArgument + ", storeArgument=" + storeArgument + ", reportingIntervalMillis="
				+ reportingIntervalMillis + ", flushCacheOnClose=" + flushCacheOnClose + "]";
	}
}
