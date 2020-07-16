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
package spade.utility.map.external.store;

import spade.utility.HelperFunctions;
import spade.utility.Converter;
import spade.utility.Result;
import spade.utility.map.external.store.db.DatabaseArgument;
import spade.utility.map.external.store.db.DatabaseHandle;
import spade.utility.map.external.store.db.DatabaseManager;
import spade.utility.profile.ReportingArgument;

/**
 * Store manager to create the Store for External map.
 */
public class StoreManager{
	
	/**
	 * Parse Store arguments
	 * 
	 * @param storeNameString name of the store as in the StoreName enum
	 * @param storeArgumentString arguments for that store
	 * @return StoreArgument or error
	 */
	public static Result<StoreArgument> parseArgument(String storeNameString, String storeArgumentString){
		if(HelperFunctions.isNullOrEmpty(storeNameString)){
			return Result.failed("NULL/Empty store name");
		}else{
			Result<ReportingArgument> reportingResult = ReportingArgument.parseReportingArgument(storeArgumentString);
			if(reportingResult.error){
				return Result.failed("Failed to parse reporting argument", reportingResult);
			}else{
				ReportingArgument reportingArgument = reportingResult.result;
				Result<StoreName> storeNameResult = HelperFunctions.parseEnumValue(StoreName.class, storeNameString, true);
				if(storeNameResult.error){
					return Result.failed("Failed store name parsing", storeNameResult);
				}else{
					StoreArgument storeArgument = null;
					StoreName storeName = storeNameResult.result;
					DatabaseManager dbManager = storeName.dbManager;
					if(dbManager == null){
						return Result.failed("Unhandled store name: " + storeName);
					}else{
						Result<? extends DatabaseArgument> dbArgumentResult = dbManager.parseArgument(storeArgumentString);
						if(dbArgumentResult.error){
							return Result.failed("Failed argument parsing for store: " + storeName, dbArgumentResult);
						}else{
							storeArgument = new StoreArgument(storeName, dbArgumentResult.result);
							storeArgument.setReportingArgument(reportingArgument);
							return Result.successful(storeArgument);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Create store for the external map
	 * 
	 * @param <K> type of key
	 * @param <V> type of value
	 * @param storeArgument argument of the store
	 * @param keyConverter byte array converter for the key type
	 * @param valueConverter byte array converter for the value type
	 * @return Store object or error
	 */
	public static <K, V> Result<? extends Store<K, V>> createStore(StoreArgument storeArgument, 
			Converter<K, byte[]> keyConverter, Converter<V, byte[]> valueConverter){
		if(storeArgument == null){
			return Result.failed("NULL store argument");
		}else{
			StoreName storeName = storeArgument.name;
			if(storeName == null){
				return Result.failed("NULL store name");
			}else{
				DatabaseManager dbManager = storeName.dbManager;
				if(dbManager == null){
					return Result.failed("Unhandled store name: " + storeName);
				}else{
					DatabaseArgument dbArgument = storeArgument.argument;
					if(dbArgument == null){
						return Result.failed("NULL database argument for store: " + storeName);
					}else{
						Result<DatabaseHandle> dbHandleResult = dbManager.createHandleFromArgument(dbArgument);
						if(dbHandleResult.error){
							return Result.failed("Database open failed", dbHandleResult);
						}else{
							DatabaseHandle dbHandle = dbHandleResult.result;
							Store<K, V> store = new DBStore<K, V>(dbHandle, keyConverter, valueConverter);
							if(storeArgument.getReportingArgument() != null){
								store = new ProfiledStore<K, V>(store, storeArgument.getReportingArgument());
							}
							return Result.successful(store);
						}
					}
				}
			}
		}
	}
}
