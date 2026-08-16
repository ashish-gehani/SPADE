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
package spade.utility.map.external.screen;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

import spade.core.BloomFilter;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * BloomFilter screen manager for external map
 */
public class BloomFilterManager extends ScreenManager{

	public static final BloomFilterManager instance = new BloomFilterManager();
	private BloomFilterManager(){}
	
	/**
	 * Create BloomFilterArgument.
	 * Sample: "expectedElements=[1-n] falsePositiveProbability=[0-1] [savePath=<writable-filepath> loadPath=<existing-filepath>]"
	 * 
	 * 'savePath' and 'loadPath' are optional.
	 * 
	 * @param arguments See above sample
	 */
	@Override
	public Result<ScreenArgument> parseArgument(String arguments){
		if(HelperFunctions.isNullOrEmpty(arguments)){
			return Result.failed("NULL/Empty arguments");
		}else{
			Result<HashMap<String, String>> mapResult = HelperFunctions.parseKeysValuesInString(arguments);
			if(mapResult.error){
				return Result.failed("Failed to parse arguments to map", mapResult);
			}else{
				return parseArgument(mapResult.result);
			}
		}
	}
	
	/**
	 * Create BloomFilterArgument.
	 * Must contains valid values for keys: 'expectedElements', 'falsePositiveProbability'.
	 * All values must be non-null and non-empty.
	 * 
	 * Optional keys: 'loadPath', 'savePath'
	 */
	@Override
	public Result<ScreenArgument> parseArgument(Map<String, String> arguments){
		if(arguments == null){
			return Result.failed("NULL arguments");
		}else if(arguments.isEmpty()){
			return Result.failed("Empty arguments");
		}else{
			String saveToPath = arguments.get(BloomFilterArgument.keySavePath);
			if(saveToPath != null){
				if(saveToPath.trim().isEmpty()){
					return Result.failed("Invalid '"+BloomFilterArgument.keySavePath+"': '"+saveToPath+"'");
				}else{
					try{
						File f = new File(saveToPath);
						if(f.exists()){
							if(f.isFile()){
								// good
							}else{
								return Result.failed("'"+BloomFilterArgument.keySavePath+"'='"+saveToPath+"' is not a regular file");
							}
						}else{
							File parentF = f.getParentFile();
							if(parentF == null){
								return Result.failed("'"+BloomFilterArgument.keySavePath+"'='"+saveToPath+"' is not a valid path");
							}else{
								if(parentF.exists()){
									// good
								}else{
									return Result.failed("'"+BloomFilterArgument.keySavePath+"'='"+saveToPath+"'. Parent directory doesn't exist");
								}
							}
						}
					}catch(Exception e){
						return Result.failed("Failed to check '"+BloomFilterArgument.keySavePath+"'='"+saveToPath+"'", e, null);
					}
				}
			}
			
			boolean loadFromFile = false;
			boolean createFromArguments = false;
			
			final String expectedElementsString = arguments.get(BloomFilterArgument.keyExpectedElements);
			final String falsePositiveString = arguments.get(BloomFilterArgument.keyFalsePositiveProbability);
			final String loadPathString = arguments.get(BloomFilterArgument.keyLoadPath);
			
			// If anything is specified then it must be valid even if it is not going to be used
			if((expectedElementsString == null && falsePositiveString != null)
					|| (expectedElementsString != null && falsePositiveString == null)){
				return Result.failed("Must specify '"+BloomFilterArgument.keyExpectedElements+"' and "
						+ "'"+BloomFilterArgument.keyFalsePositiveProbability+"' together");
			}else if(expectedElementsString == null && falsePositiveString == null){
				createFromArguments = false;
			}else{ // both non-null
				createFromArguments = true;
			}
			
			loadFromFile = loadPathString != null;
			
			// Try loading from file first. If no file then try loading from create-args. If file exists then use it and ignore create-args.
			
			// Preference given to loading from file
			if(loadFromFile){
				try{
					File f = new File(loadPathString);
					if(!f.exists()){
						if(createFromArguments){
							// Don't throw any error and see if can be created from arguments
						}else{
							return Result.failed("'"+BloomFilterArgument.keyLoadPath+"'='"+loadPathString+"' does not exist");
						}
					}else{
						if(!f.isFile()){
							return Result.failed("'"+BloomFilterArgument.keyLoadPath+"'='"+loadPathString+"' is not a regular file");
						}else{
							return Result.successful(new BloomFilterArgument.LoadFromFile(loadPathString, saveToPath));
						}
					}
				}catch(Exception e){
					return Result.failed("Failed to check '"+BloomFilterArgument.keyLoadPath+"'='"+loadPathString+"'", e, null);
				}
			}
			
			// Only here if not loaded from file already
			if(createFromArguments){
				Result<Long> expectedElementsResult = HelperFunctions.parseLong(
						arguments.get(BloomFilterArgument.keyExpectedElements), 10, 1, Integer.MAX_VALUE);
				if(expectedElementsResult.error){
					return Result.failed("Failed to parse '"+BloomFilterArgument.keyExpectedElements+"'", expectedElementsResult);
				}else{
					int expectedElements = expectedElementsResult.result.intValue();
					Result<Double> falsePositiveProbResult = HelperFunctions.parseDouble(
							arguments.get(BloomFilterArgument.keyFalsePositiveProbability), 0, 1);
					if(falsePositiveProbResult.error){
						return Result.failed("Failed to parse '"+BloomFilterArgument.keyFalsePositiveProbability+"'", falsePositiveProbResult);
					}else{
						double falsePositiveProbability = falsePositiveProbResult.result;
						return Result.successful(new BloomFilterArgument.CreateFromArgs(expectedElements, falsePositiveProbability, 
								saveToPath));
					}
				}
			}
			
			return Result.failed("Must specify ('"+BloomFilterArgument.keyExpectedElements+"' and "
					+ "'"+BloomFilterArgument.keyFalsePositiveProbability+"') and/or "
							+ "('"+BloomFilterArgument.keyLoadPath+"')");
		}
	}

	/**
	 * Validates the passed argument as the correct argument for this screen manager
	 * 
	 * @param genericArgument ScreenArgument must be BloomFilterArgument
	 * @return BloomFilterArgument object otherwise error
	 */
	private Result<BloomFilterArgument> validateArgument(final ScreenArgument genericArgument){
		if(genericArgument == null){
			return Result.failed("NULL argument");
		}else if(genericArgument.getClass().equals(BloomFilterArgument.CreateFromArgs.class)){
			BloomFilterArgument.CreateFromArgs argument = (BloomFilterArgument.CreateFromArgs)genericArgument;
			return Result.successful(argument);
		}else if(genericArgument.getClass().equals(BloomFilterArgument.LoadFromFile.class)){
			BloomFilterArgument.LoadFromFile argument = (BloomFilterArgument.LoadFromFile)genericArgument;
			if(HelperFunctions.isNullOrEmpty(argument.loadPath)){
				return Result.failed("NULL/Empty path to load bloomfilter from");
			}else{
				return Result.successful(argument);
			}
		}else{
			return Result.failed("Screen argument class must be BloomFilterArgument but is '"+genericArgument.getClass()+"'");
		}
	}
	
	/**
	 * If BloomFilterArgument.LoadFromFile then tries to load the bloomfilter from file
	 * If BloomFilterArgument.CreateFromArgs then tries to create fresh bloom filter
	 * 
	 * @param ScreenArgument must be BloomFilterArgument
	 * @return Screen object or error
	 */
	@Override
	public <K> Result<Screen<K>> createFromArgument(ScreenArgument genericArgument){
		Result<BloomFilterArgument> validResult = validateArgument(genericArgument);
		if(validResult.error){
			return Result.failed("Invalid screen argument", validResult);
		}else{
			BloomFilterArgument argument = validResult.result;
			if(argument == null){
				return Result.failed("NULL argument");
			}else{
				BloomFilter<K> bloomFilter = null;
				if(argument.getClass().equals(BloomFilterArgument.CreateFromArgs.class)){
					BloomFilterArgument.CreateFromArgs createArg = (BloomFilterArgument.CreateFromArgs)argument;
					bloomFilter = new BloomFilter<K>(createArg.falsePositiveProbability, createArg.expectedElements);
				}else if(argument.getClass().equals(BloomFilterArgument.LoadFromFile.class)){
					BloomFilterArgument.LoadFromFile loadArg = (BloomFilterArgument.LoadFromFile)argument;
					Result<BloomFilter<K>> resultBloomFilter = loadBloomFilterFromFile(loadArg.loadPath);
					if(resultBloomFilter.error){
						return Result.failed("Invalid load path", resultBloomFilter);
					}else{
						bloomFilter = resultBloomFilter.result;
					}
				}else{
					return Result.failed("Screen argument class must be BloomFilterArgument but is '"+genericArgument.getClass()+"'");
				}
				if(bloomFilter == null){
					return Result.failed("NULL BloomFilter for unknown reason");
				}else{
					BloomFilterScreen<K> screen = new BloomFilterScreen<K>(argument.savePath, bloomFilter);
					return Result.successful(screen);
				}
			}
		}
	}
	
	/**
	 * Loads BloomFilter from path
	 * 
	 * @param <K> key type
	 * @param path path of the file to load bloomfilter from
	 * @return bloomfilter object
	 */
	private <K> Result<BloomFilter<K>> loadBloomFilterFromFile(String path){
		try{
			FileInputStream fis = new FileInputStream(new File(path));
			ObjectInputStream ois = new ObjectInputStream(fis);
			@SuppressWarnings("unchecked")
			BloomFilter<K> bloomFilter = (BloomFilter<K>)ois.readObject();
			ois.close();
			fis.close();
			if(bloomFilter == null){
				return Result.failed("NULL BloomFilter loadede from path: '"+path+"'");
			}else{
				return Result.successful(bloomFilter);
			}
		}catch(Exception e){
			return Result.failed("Failed to load BloomFilter from path: '"+path+"'", e, null);
		}
	}

}
