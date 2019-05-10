/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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

import java.io.Serializable;
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;

/**
 * This interface must be implemented by classes that need to be used as external storage for ExternalMemoryMap class
 *
 * @param Object to serialize against a String key
 */

public interface ExternalStore<V extends Serializable>{
	
	/**
	 * A function to get the value storage against the provided key
	 * @param key Key to look for
	 * @return Object value against the key
	 * @throws Exception Any implementation dependent exception
	 */
	public V get(String key) throws Exception;
	/**
	 * A function to add a key value pair
	 * @param key
	 * @param value
	 * @throws Exception Any implementation dependent exception
	 */
	public void put(String key, V value) throws Exception;
	/**
	 * A function to remove the key value if it exists
	 * @param key
	 * @throws Exception Any implementation dependent exception
	 */
	public void remove(String key) throws Exception;
	/**
	 * A function to remove all key value pairs in the external storage
	 * @throws Exception Any implementation dependent exception
	 */
	public void clear() throws Exception;
	/**
	 * A function to close the store
	 * @throws Exception Any implementation dependent exception
	 */
	public void close() throws Exception;
	/**
	 * Delete persisted data (if any)
	 */
	public void delete() throws Exception;
	/**
	 * Return size in bytes of data persisted
	 */
	public BigInteger sizeInBytesOfPersistedData() throws Exception;


	public static void main(String[] args) throws Exception{
		ExternalStoreTestArguments arguments = ExternalStoreTestArguments.parse(args);
		if(arguments != null){
			int i = 0;
			try{
				for(; i < arguments.stores.size(); i++){
					ExternalStore<Integer> store = arguments.stores.get(i);
					System.out.println("**************** Started test");
					test(store, arguments.totalElements);
					System.out.println("**************** Finished test");
				}
			}catch(Exception e){
				System.err.println("**************** Tests stopped at test number: '"+i+"'");
				e.printStackTrace();
				for(; i < arguments.stores.size(); i++){
					ExternalStore<Integer> store = arguments.stores.get(i);
					try{ store.close(); }catch(Exception e1){}
					try{
						store.delete();
					}catch(Exception e2){
						System.out.println("**************** Failed to delete store");
						e2.printStackTrace();
					}
				}
			}
		}
	}

	public static void test(ExternalStore<Integer> store, int total) throws Exception{
		System.out.println("TEST CLASS: '" + store.getClass().getName() + "'");
                long start = 0;
                start = System.currentTimeMillis();
                for(int i = 0; i < total; i++){
                        if(store.get(String.valueOf(i)) != null){
                                System.err.println("Unexpected value for key: " + i);
                        }
                }
                System.out.println("Total 'get' (fresh empty store) time: " + (System.currentTimeMillis() - start) + " ms");

                start = System.currentTimeMillis();
                for(int i = 0; i < total; i++){
                        store.put(String.valueOf(i), total-i);
                }
                System.out.println("Total 'put' time: " + (System.currentTimeMillis() - start) + " ms");

                start = System.currentTimeMillis();
                for(int i = 0; i < total; i++){
                        Integer x = store.get(String.valueOf(i));
                        if(x != total - i){
                                System.err.println("Incorrect value for " + i + ": " + x);
                        }
                }
                System.out.println("Total 'get' time: " + (System.currentTimeMillis() - start) + " ms");

                start = System.currentTimeMillis();
                for(int i = 0; i < total; i++){
                        store.remove(String.valueOf(i));
                }
                System.out.println("Total 'remove' time: " + (System.currentTimeMillis() - start) + " ms");

                start = System.currentTimeMillis();
                for(int i = 0; i < total; i++){
                        if(store.get(String.valueOf(i)) != null){
                                System.err.println("Key not removed: " + i);
                        }
                }
                System.out.println("Total 'get' (after removing all) time: " + (System.currentTimeMillis() - start) + " ms");

                System.out.println("Size on disk (before close): " + getPrintableSize(store));

                store.close();

                System.out.println("Size on disk (after close): " + getPrintableSize(store));

                store.delete();
        }

        public static String getPrintableSize(ExternalStore<?> store) throws Exception{
                BigInteger sizeBytes = store.sizeInBytesOfPersistedData();
                String displaySize = FileUtils.byteCountToDisplaySize(sizeBytes);
                return displaySize;
        }

}
