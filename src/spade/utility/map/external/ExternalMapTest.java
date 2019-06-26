package spade.utility.map.external;

import spade.utility.CommonFunctions;
import spade.utility.Serializable2ByteArrayConverter;
import spade.utility.Result;
import spade.utility.map.external.store.StoreName;

public class ExternalMapTest{

	public static String createArg(String... args){
		if(args == null){
			return null;
		}else if(args.length == 0){
			return "";
		}else{
			String s = "";
			for(int a = 0; a < args.length; a+=2){
				String k = args[a];
				String v = args[a+1];
				s += k + "=" + v + " ";
			}
			return s.trim();
		}
	}
	
	public static void main(String[] args) throws Exception{
		String mapId = "mapId";//"artifacts";
//		Map<String, String> map = new HashMap<String, String>();
//		
//		map.put(mapId + "." + ExternalMapArgument.keyReportingSeconds, "5");
//		
//		String screenName = ScreenName.BloomFilter.toString(), screenArgument = 
//				createArg(
//						//BloomFilterArgument.keyLoadPath, "/tmp/bftmpsave",
//						BloomFilterArgument.keyExpectedElements, "1000",
//						BloomFilterArgument.keyFalsePositiveProbability, "0.00001"
////						ReportingArgument.keyReportingId, "bloomfilter",
////						ReportingArgument.keyReportingSeconds, "1"
////						BloomFilterArgument.keySavePath, "/tmp/bftmpsave"
//						);
//		map.put(mapId + ".screenName", screenName);
//		map.put(mapId + ".screenArgument", screenArgument);
//		
//		map.put(mapId + ".cacheName", "LRU");
//		map.put(mapId + ".cacheArgument", "size=10000");
//		
////		String storeName = StoreName.MemoryDB.toString(), storeArgument = null;
////		String storeName = StoreName.NullDB.toString(), storeArgument = null;
//		String storeName = StoreName.LevelDB.toString(), storeArgument = 
//				createArg(
////						ReportingArgument.keyReportingId, "leveldb",
////						ReportingArgument.keyReportingSeconds, "5",
//						LevelDBArgument.keyDatabasePath, null,
//						LevelDBArgument.keyDeleteDbOnClose, "true"
//						);
////		String storeName = StoreName.MemoryDB.toString(), storeArgument = null;
//		map.put(mapId + ".storeName", storeName);
//		map.put(mapId + ".storeArgument", storeArgument);
		
//		Result<ExternalMapArgument> argumentResult = ExternalMapManager.parseArgumentFromMap(mapId, map);
		Result<ExternalMapArgument> argumentResult = ExternalMapManager.parseArgumentFromFile(mapId, "/tmp/cfgmapfil");
		System.out.println(argumentResult);
		//System.exit(0);
		if(!argumentResult.error){
			Serializable2ByteArrayConverter<String> converter = new Serializable2ByteArrayConverter<String>();
			Result<ExternalMap<String, String>> mapResult = ExternalMapManager.create(argumentResult.result, 
					converter, converter);
			System.out.println(mapResult);
			if(!mapResult.error){
				ExternalMap<String, String> externalMap = mapResult.result;
				
				int start = 0;
				int stop = 9900000;
				for(int i = start; i < stop; i++){
					externalMap.put(String.valueOf(i), String.valueOf(stop-i));
//					Thread.sleep(100);
				}
				
				for(int i = start; i < stop; i++){
					String x = String.valueOf(i);
					String shouldbe = String.valueOf(stop-i);
					String result = externalMap.get(x);
					if(!CommonFunctions.objectsEqual(shouldbe, result)){
						System.out.println(x + "," + result + "," + shouldbe);
					}
//					Thread.sleep(100);
				}
				
				externalMap.close(true);
			}
		}else{
			System.out.println(argumentResult.toErrorString());
		}
		
		StoreName.shutdownAll();
		
//		byte[] x = new byte[]{0,1};
//		byte[] y = new byte[]{0,1};
//		Map<byte[], byte[]> xx = new HashMap<byte[], byte[]>();
//		xx.put(x, new byte[]{2,3});
//		System.out.println(xx.get(y)[0]);
	}
}
