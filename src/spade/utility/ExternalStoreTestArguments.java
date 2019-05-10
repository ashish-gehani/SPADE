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

import spade.utility.BerkeleyDB;
import spade.utility.ExternalStore;
import spade.utility.LevelDBStore;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExternalStoreTestArguments{

	private static interface ExternalStoreInitializer<T extends Serializable>{
		public ExternalStore<T> init(String dbPath, String dbName) throws Exception;
	}

	private final static Map<String, ExternalStoreInitializer<Integer>> allStoreInitializers = new HashMap<String, ExternalStoreInitializer<Integer>>(){
		{
			// TODO - Directory being created inside the caller of these functions
			put(LevelDBStore.class.getName(), new ExternalStoreInitializer<Integer>(){
				public ExternalStore<Integer> init(String dbPath, String dbName) throws Exception{
					return new LevelDBStore<Integer>(dbPath, dbName);
				}
			});

			put(BerkeleyDB.class.getName(), new ExternalStoreInitializer<Integer>(){
				public ExternalStore<Integer> init(String dbPath, String dbName) throws Exception{
					return new BerkeleyDB<Integer>(dbPath, dbName, null);
				}
			});

		}
	};
	private final static String[] allStoreClassNames = {LevelDBStore.class.getName(), BerkeleyDB.class.getName()};

	public final int totalElements;
	public final String rootDirPath;
	public final List<ExternalStore<Integer>> stores;

	private ExternalStoreTestArguments(int totalElements, String rootDirPath, List<ExternalStore<Integer>> stores){
		this.totalElements = totalElements;
		this.rootDirPath = rootDirPath;
		this.stores = stores;
	}

	private static boolean validateGlobals(){
		Set<String> a = allStoreInitializers.keySet();
		Set<String> b = new HashSet<String>(Arrays.asList(allStoreClassNames));
		if(a.containsAll(b) && b.containsAll(a)){
			return true;
		}
		System.err.println("Globals mismatch: '"+a+"' and '"+b+"'");
		return false;
	}

	public static ExternalStoreTestArguments parse(String[] args){
		if(validateGlobals()){
			if(args != null){
				if(args.length >= 2){
					String totalElementsArgument = args[0];
					String rootDirPathArgument = args[1];
					String[] qualifiedStoreClassNamesCSV = allStoreClassNames;
					if(args.length > 2){
						qualifiedStoreClassNamesCSV = args[2].split(",");
					}
					ExternalStoreTestArguments arguments = parseArguments(totalElementsArgument, rootDirPathArgument, qualifiedStoreClassNamesCSV);
					return arguments;
				}
			}
			String helpString = getHelpAsString();
			System.err.println(helpString);
			return null;
		}else{
			return null;
		}
	}

	private static ExternalStoreTestArguments parseArguments(String totalElementsArgument, String rootDirPathArgument, String[] qualifiedStoreClassNameArgument){
		Integer totalElements = null;
		try{
			totalElements = Integer.parseInt(totalElementsArgument);
			if(totalElements < 0){
				throw new Exception("Total elements argument must be non-negative: '" + totalElements + "'");
			}
			try{
				File f = new File(rootDirPathArgument);
				if(f.isDirectory() && f.exists()){
					List<ExternalStore<Integer>> stores = new ArrayList<ExternalStore<Integer>>();
					boolean failed = false;
					for(String qualifiedStoreClassName : qualifiedStoreClassNameArgument){
						ExternalStoreInitializer<Integer> initializer = allStoreInitializers.get(qualifiedStoreClassName);
						if(initializer != null){
							try{
								String dbName = System.currentTimeMillis() + "_" + System.nanoTime();
								String dbPath = rootDirPathArgument + File.separator + dbName;
								File dbFile = new File(dbPath);
								if(dbFile.mkdir()){
									try{									
										ExternalStore<Integer> store = initializer.init(dbPath, dbName);
										if(store == null){
											System.err.println("Initializer returned 'null' store for name: '" + qualifiedStoreClassName + "'");
											failed = true;
										}else{
											stores.add(store);
										}
									}catch(Exception e9){
										System.err.println("Failed to initialize store for name: '" + qualifiedStoreClassName+ "'");
										e9.printStackTrace();
										failed = true;
									}
								}else{
									System.err.println("Failed to create directory: '"+dbPath+"'");
									failed = true;
								}
							}catch(Exception e4){
								System.err.println("Failed to create directory");
								e4.printStackTrace();
								failed = true;
							}
						}else{
							System.err.println("No initializer for class name: '" + qualifiedStoreClassName + "'");
							failed = true;
						}
					}
					if(!failed){
						ExternalStoreTestArguments arguments = new ExternalStoreTestArguments(totalElements, rootDirPathArgument, stores);
						String str = String.format("Using arguments -> 'total-elements'='%s', 'root-dir'='%s', 'test-classes'='%s'", 
								totalElements, f.getAbsolutePath(), Arrays.asList(qualifiedStoreClassNameArgument));
						return arguments;
					}else{
						for(ExternalStore<Integer> store : stores){
							try{
								try{ store.close(); }catch(Exception e5){ }
								store.delete();
							}catch(Exception e3){
								System.err.println("Failed to delete a store in dir: '" + f.getAbsolutePath() + "'");
								e3.printStackTrace();
							}
						}
					}
				}else{
					System.err.println("Root path must be directory and must exist: '" + rootDirPathArgument + "'");
				}
			}catch(Exception e2){
				System.err.println("Root path check failed: '" + rootDirPathArgument + "'");
				e2.printStackTrace();
			}
		}catch(Exception e){
			System.err.println("Invalid Total elements argument: '" + totalElementsArgument + "'");
			e.printStackTrace();
		}
		return null;
	}

	private static String getHelpAsString(){
		String tab = "\t";
		String nl = System.getProperty("line.separator");
		String help = "Usage: ExternalStoreTest <total-elements> <root-dir> <test-classes>" + nl;
		help += tab + "total-elements: total number of elements to test with as 'integer'" + nl;
		help += tab + "root-dir: path to create the test directory in and must exist" + nl;
		help += tab + "test-classes: comma-separated qualified names of classes to test" + nl;
		return help;
	}

}
