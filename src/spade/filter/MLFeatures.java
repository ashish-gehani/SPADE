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
package spade.filter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.utility.CommonFunctions;

/**
 * A filter to drop annotations passed in arguments.
 *
 * Arguments format: keys=name,version,epoch
 *
 * Note 1: Filter relies on two things for successful use of Java Reflection API:
 * 1) The vertex objects passed have an empty constructor
 * 2) The edge objects passed have a constructor with source vertex and a destination vertex (in that order)
 *
 * Note 2: Creating copy of passed vertices and edges instead of just removing the annotations from the existing ones
 * because the passed vertices and edges might be in use by some other classes specifically the reporter that
 * generated them. In future, maybe shift the responsibility of creating a copy to Kernel before it sends out
 * the vertices and edges to filters.
 */
public class MLFeatures extends AbstractFilter{

	private Logger logger = Logger.getLogger(this.getClass().getName());

	private HashMap<String,HashMap<String,Double>> features = new HashMap<>();
	private HashMap<String,String> agentsName = new HashMap<>();
	private HashMap<String,String> processName = new HashMap<>();
	private HashMap<String,String> processCommandline = new HashMap<>();
	private HashMap<String,String> firstActivity = new HashMap<>();
	private HashMap<String,String> lastUsed = new HashMap<>();
	private HashMap<String,String> lastWgb = new HashMap<>();
	private HashMap<String,HashSet<String>> fileUsed = new HashMap<>();
	private HashMap<String,HashSet<String>> fileWgb = new HashMap<>();
	private HashMap<String,HashSet<String>> extensionUsed = new HashMap<>();
	private HashMap<String,HashSet<String>> extensionWgb = new HashMap<>();
	private HashMap<String,HashSet<String>> directoryUsed = new HashMap<>();
	private HashMap<String,HashSet<String>> directoryWgb = new HashMap<>();
	private HashMap<Integer,Double> labelMap = new HashMap<>();
	private HashMap<String,Integer> processIdentifierToHashcode = new HashMap<>();
	///////////////////////////////////////////////
	private String maliciousName = null;
	private  String filepathFeatures = "Default.csv";
	//////////////////////////////////////////////
	private final String USER = "User";
	private final String EXE = "exe";
	private final String DLL = "dll";
	private final String DAT = "dat";
	private final String BIN = "bin";
	private final String TIME = "time";
	private final String PROCESS = "Process";
	private final String PROCESS_IDENTIFIER = "pid";
	private final String CLASS = "class";
	private final String FILE_SYSTEM = "File System";
	private final String REGISTRY = "Registry";
	private final String PATH = "path";
	private final String DETAIL = "detail";
	private final String NAME = "name";
	private final String COUNT_NETWORK_SEND = "countNetworkSend";
	private final String SUBTYPE = "subtype";
	private final String NETWORK = "network";
	private final String DURATION = "duration";
	private final String COMMANDLINE = "commandline";
	///////////////////////////////////////////////
	private final String USED = "Used";
	private final String WCB = "WasControlledBy";
	private final String WTB = "WasTriggeredBy";
	private final String WGB = "WasGeneratedBy";
	//////////////////////////////////////////////
	private static int SCALE_TIME = 10000000;
	private final Double INITIAL_ZERO = (double) 0;
	private final String eol = System.getProperty("line.separator");
	/////////////////////////////////////////////
	private final String LABEL = "label";
	private final String MALICIOUS = "bad";
	private final String BENIGN = "good";
	private final String TAINTED = "tainted";
	private final Double BAD = 1.0;
	private final Double GOOD = 0.0;
	//////////////////////////////////////////////
	private final String COUNT_USED = "countUsed";
	private final String COUNT_WGB = "countWgb";
	private final String COUNT_WTB = "countWasTriggeredBy";
	private final String AVG_DURATION_BETWEEN_TWO_USED = "avgDurationBetweenTwoUsed";
	private final String AVG_DURATION_BETWEEN_TWO_WGB = "avgDurationBetweenTwoWgb";
	private final String AVG_DURATION_USED = "avgDurationUsed";
	private final String AVG_DURATION_WGB = "avgDurationWgb";
	private final String COUNT_FILESYSTEM_USED = "countFilesystemUsed";
	private final String COUNT_FILESYSTEM_WGB = "countFilesystemWgb";
	private final String COUNT_REGISTRY_USED = "countRegistryUsed";
	private final String COUNT_REGISTRY_WGB = "countRegistryWgb";
	private final String LIFE_DURATION = "lifeDuration";
	private final String TOTAL_LENGTH_READ = "totalLengthRead";
	private final String TOTAL_LENGTH_WRITTEN = "totalLengthWritten";
	private final String COUNT_OF_USED_FILES = "countOfUsedFiles";
	private final String COUNT_OF_WGB_FILES = "countOfWgbFiles";
	private final String COUNT_EXTENSION_TYPE_USED = "countExtensionTypeUsed";
	private final String COUNT_EXTENSION_TYPE_WGB = "countExtensionTypeWgb";
	private final String COUNT_EXE_DAT_DLL_BIN_USED = "countExeDatDllBinUsed";
	private final String COUNT_EXE_DAT_DLL_BIN_WGB = "countExeDatDllBinWgb";
	private final String COUNT_USED_NOT_NETWORK = "countUsedNotNetwork";
	private final String COUNT_WGB_NOT_NETWORK = "countWgbNotNetwork";
	private final String COUNT_NETWORK_RECEIVE = "countNetworkReceive";
	private final String COUNT_OF_DIRECTORIES_USED = "countOfDirectoriesUsed";
	private final String COUNT_OF_DIRECTORIES_WGB = "countOftDirectoriesWgb";


	public boolean initialize(String arguments){
		if(arguments != null){
			String[] argumentsTab = arguments.split(" ");
			try{
			filepathFeatures = argumentsTab[1];
			if(argumentsTab.length > 2){
				maliciousName = argumentsTab[2];
			}
			}catch(Exception e){
				logger.log(Level.WARNING, "invalid arguments form");
			}
		}

		return true;

	}

	@Override
	public boolean shutdown(){
		Set<String> names = new HashSet<String>(Arrays.asList(COUNT_USED,COUNT_WGB,AVG_DURATION_BETWEEN_TWO_USED,AVG_DURATION_BETWEEN_TWO_WGB,AVG_DURATION_USED,
				AVG_DURATION_WGB,COUNT_FILESYSTEM_USED,COUNT_FILESYSTEM_WGB,LIFE_DURATION,TOTAL_LENGTH_READ,TOTAL_LENGTH_WRITTEN,COUNT_OF_USED_FILES,
				COUNT_OF_WGB_FILES,COUNT_EXTENSION_TYPE_USED,COUNT_EXTENSION_TYPE_WGB,COUNT_EXE_DAT_DLL_BIN_USED,COUNT_EXE_DAT_DLL_BIN_WGB,COUNT_WTB,COUNT_REGISTRY_USED,
				COUNT_REGISTRY_WGB,COUNT_NETWORK_RECEIVE,COUNT_NETWORK_SEND,COUNT_OF_DIRECTORIES_USED,COUNT_OF_DIRECTORIES_WGB));

		try (Writer writer = new FileWriter(filepathFeatures)) {

		   for (String name : names){
			   writer.append(name)
			   		 .append(';');
		   }
		   writer.append(USER)
		   		 .append(';')
		   		 .append(NAME)
		   		 .append(';')
		   		 .append(COMMANDLINE)
		   		 .append(';')
		   		 .append(PROCESS_IDENTIFIER)
		   		 .append(';')
		   		 .append(LABEL)
		   		 .append(eol);
		   for (String key : features.keySet()) {

			   HashMap<String,Double> current = features.get(key);

			   for(String column : names  ){
				   writer.append(Double.toString(current.get(column)))
		              	 .append(';');
			   }
			   writer.append(agentsName.get(key))
			   		 .append(';')
			   		 .append(processName.get(key))
			   		 .append(';')
			   		 .append(processCommandline.get(key).replace(';', ' '))
			   		 .append(';')
			   		 .append(key)
			   		 .append(';')
			   		 .append(Double.toString(labelMap.get(processIdentifierToHashcode.get(key))))
			         .append(eol);
		   }
		} catch (IOException ex) {
		  ex.printStackTrace(System.err);
		}


		return true;
	}

	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(incomingVertex != null){
			if(incomingVertex.type().equals(PROCESS)){

				String processPid = incomingVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String,Double> initialFeatures = new HashMap<>();
				initialFeatures.put(COUNT_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_WGB, INITIAL_ZERO);
				initialFeatures.put(AVG_DURATION_USED,INITIAL_ZERO);
				initialFeatures.put(AVG_DURATION_WGB, INITIAL_ZERO);
				initialFeatures.put(COUNT_FILESYSTEM_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_FILESYSTEM_WGB, INITIAL_ZERO);
				initialFeatures.put(AVG_DURATION_BETWEEN_TWO_USED,Double.MAX_VALUE);
				initialFeatures.put(AVG_DURATION_BETWEEN_TWO_WGB,Double.MAX_VALUE);
				initialFeatures.put(TOTAL_LENGTH_READ, INITIAL_ZERO);
				initialFeatures.put(TOTAL_LENGTH_WRITTEN,INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_USED_FILES, INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_WGB_FILES, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXTENSION_TYPE_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXTENSION_TYPE_WGB, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXE_DAT_DLL_BIN_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXE_DAT_DLL_BIN_WGB, INITIAL_ZERO);
				initialFeatures.put(COUNT_USED_NOT_NETWORK, INITIAL_ZERO);
				initialFeatures.put(COUNT_WGB_NOT_NETWORK, INITIAL_ZERO);
				initialFeatures.put(LIFE_DURATION, INITIAL_ZERO);
				initialFeatures.put(COUNT_WTB, INITIAL_ZERO);
				initialFeatures.put(COUNT_REGISTRY_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_REGISTRY_WGB, INITIAL_ZERO);
				initialFeatures.put(COUNT_NETWORK_RECEIVE, INITIAL_ZERO);
				initialFeatures.put(COUNT_NETWORK_SEND, INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_DIRECTORIES_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_DIRECTORIES_WGB, INITIAL_ZERO);
				features.put(processPid,initialFeatures);

				fileUsed.put(processPid, new HashSet<>());
				fileWgb.put(processPid, new HashSet<>());
				extensionUsed.put(processPid, new HashSet<>());
				extensionWgb.put(processPid, new HashSet<>());
				directoryUsed.put(processPid, new HashSet<>());
				directoryWgb.put(processPid, new HashSet<>());
				processName.put(processPid, incomingVertex.getAnnotation(NAME));
				processCommandline.put(processPid, incomingVertex.getAnnotation(COMMANDLINE));
				processIdentifierToHashcode.put(processPid, incomingVertex.hashCode());
				if((maliciousName != null) && (incomingVertex.getAnnotation(NAME).equals(maliciousName))){
					labelMap.put(incomingVertex.hashCode(), BAD);
				}else{
					labelMap.put(incomingVertex.hashCode(), GOOD);
				}
			}else{
				labelMap.put(incomingVertex.hashCode(), GOOD);
			}

			putInNextFilter(incomingVertex);
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null && incomingEdge.getSourceVertex() != null && incomingEdge.getDestinationVertex() != null){

			try{

			if(labelMap.get(incomingEdge.getDestinationVertex().hashCode()) != GOOD){
				if(labelMap.get(incomingEdge.getSourceVertex().hashCode()) == GOOD){
					labelMap.put(incomingEdge.getSourceVertex().hashCode(),labelMap.get(incomingEdge.getDestinationVertex().hashCode())/1.4 );
				}
			}

			if (incomingEdge.getSourceVertex().type().equals(PROCESS)) {

				AbstractVertex sourceProcessVertex = incomingEdge.getSourceVertex();
				AbstractVertex destinationVertex = incomingEdge.getDestinationVertex();
				String ProcessPid = sourceProcessVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String, Double> sourceProcess = features.get(ProcessPid);

				if (incomingEdge.type().equals(USED)){

					String time = incomingEdge.getAnnotation(TIME);

					double count_used = sourceProcess.get(COUNT_USED);

					if(!firstActivity.containsKey(ProcessPid)){
						firstActivity.put(ProcessPid, time);
					}
					sourceProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(time,firstActivity.get(ProcessPid)));

					if (count_used != 0){
						double avgDurationBetweenTwoUsed = sourceProcess.get(AVG_DURATION_BETWEEN_TWO_USED);
						String lastTimeUsed = lastUsed.get(ProcessPid);
						sourceProcess.put(AVG_DURATION_BETWEEN_TWO_USED,(avgDurationBetweenTwoUsed*(count_used-1) + differenceBetweenTwoTimes(time, lastTimeUsed))/count_used );
					}
					lastUsed.put(ProcessPid, time);





					try{
					double duration = Double.parseDouble(incomingEdge.getAnnotation(DURATION));
					double currentDurationMean = sourceProcess.get(AVG_DURATION_USED);
					double count_used_not_network = sourceProcess.get(COUNT_USED_NOT_NETWORK);

					sourceProcess.put(AVG_DURATION_USED,(currentDurationMean*count_used_not_network + duration)/(count_used_not_network+1) );
					sourceProcess.put(COUNT_USED_NOT_NETWORK, count_used_not_network + 1);
					}catch(Exception e){}


					sourceProcess.put(COUNT_USED, count_used + 1);




					double lengthRead = getLengthFromDetailAnnotation(incomingEdge.getAnnotation(DETAIL));
					double currentLength = sourceProcess.get(TOTAL_LENGTH_READ);
					sourceProcess.put(TOTAL_LENGTH_READ, currentLength + lengthRead);

					HashSet<String> fileUsedByProcess = fileUsed.get(ProcessPid);
					String filePath = destinationVertex.getAnnotation(PATH);
					if(filePath != null){
						if(!fileUsedByProcess.contains(filePath)){

							fileUsedByProcess.add(filePath);
							double countFileUsed = sourceProcess.get(COUNT_OF_USED_FILES);
							sourceProcess.put(COUNT_OF_USED_FILES, countFileUsed + 1);

						}
						HashSet<String> directoriesUsedByProcess = directoryUsed.get(ProcessPid);
						String directory = getDirectory(filePath);
						if(!directoriesUsedByProcess.contains(directory)){

							directoriesUsedByProcess.add(directory);
							double countDirectoryUsed = sourceProcess.get(COUNT_OF_DIRECTORIES_USED);
							sourceProcess.put(COUNT_OF_DIRECTORIES_USED, countDirectoryUsed +1);
						}
					}
					try{
						if(destinationVertex.getAnnotation(CLASS).equals(FILE_SYSTEM)){
							double count_filesystem = sourceProcess.get(COUNT_FILESYSTEM_USED);
							sourceProcess.put(COUNT_FILESYSTEM_USED, count_filesystem + 1);

							HashSet<String> extensionUsedByProcess = extensionUsed.get(ProcessPid);
							String extension = getExtension(filePath);
							if(!extensionUsedByProcess.contains(extension)){
								extensionUsedByProcess.add(extension);
								double countExtension = sourceProcess.get(COUNT_EXTENSION_TYPE_USED);
								sourceProcess.put(COUNT_EXTENSION_TYPE_USED, countExtension + 1);
							}

							if(extension.equals(EXE) || extension.equals(DLL) || extension.equals(DAT) || extension.equals(BIN)){
								double count_exe_dll = sourceProcess.get(COUNT_EXE_DAT_DLL_BIN_USED);
								sourceProcess.put(COUNT_EXE_DAT_DLL_BIN_USED, count_exe_dll + 1);
							}

						}else if(destinationVertex.getAnnotation(CLASS).equals(REGISTRY)){
							double count_registry = sourceProcess.get(COUNT_REGISTRY_USED);
							sourceProcess.put(COUNT_REGISTRY_USED, count_registry + 1);
						}


					}catch(Exception e){}

					try{
						if(destinationVertex.getAnnotation(SUBTYPE).equals(NETWORK)){
							double countNetworkReceive = sourceProcess.get(COUNT_NETWORK_RECEIVE);
							sourceProcess.put(COUNT_NETWORK_RECEIVE, countNetworkReceive + 1);
						}
					}catch(Exception e){}


				}else if (incomingEdge.type().equals(WCB)){

					agentsName.put(ProcessPid,destinationVertex.getAnnotation(USER));

				}else if(incomingEdge.type().equals(WTB)){
					String time = incomingEdge.getAnnotation(TIME);
					String destinationPid = destinationVertex.getAnnotation(PROCESS_IDENTIFIER);
					HashMap<String,Double> destinationProcess = features.get(destinationPid);
					double countWtb = destinationProcess.get(COUNT_WTB);

					destinationProcess.put(COUNT_WTB, countWtb + 1);

					if(labelMap.get(incomingEdge.getDestinationVertex().hashCode()) == BAD){
						labelMap.put(incomingEdge.getSourceVertex().hashCode(),BAD);
					}

					if(!firstActivity.containsKey(destinationPid)){
						firstActivity.put(destinationPid, time);
					}
					destinationProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(time,firstActivity.get(destinationPid)));
				}





			}else if (incomingEdge.getDestinationVertex().type().equals(PROCESS)){

				AbstractVertex destinationProcessVertex = incomingEdge.getDestinationVertex();
				AbstractVertex sourceVertex = incomingEdge.getSourceVertex();
				String ProcessPid = destinationProcessVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String, Double> destinationProcess = features.get(ProcessPid);

				String time = incomingEdge.getAnnotation(TIME);

				if(!firstActivity.containsKey(ProcessPid)){
					firstActivity.put(ProcessPid, time);
				}

				destinationProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(time,firstActivity.get(ProcessPid)));

				if (incomingEdge.type().equals(WGB)){

					double count_wgb = destinationProcess.get(COUNT_WGB);

					if (count_wgb != 0){
						double avgDurationBetweenTwoWgb = destinationProcess.get(AVG_DURATION_BETWEEN_TWO_WGB);
						String lastTimeWgb = lastWgb.get(ProcessPid);
						destinationProcess.put(AVG_DURATION_BETWEEN_TWO_WGB,(avgDurationBetweenTwoWgb*(count_wgb-1) + differenceBetweenTwoTimes(time, lastTimeWgb))/count_wgb );

					}
					lastWgb.put(ProcessPid, time);



					try{
					double duration = Double.parseDouble(incomingEdge.getAnnotation(DURATION));
					double currentDurationMean = destinationProcess.get(AVG_DURATION_WGB);
					double count_wgb_not_network = destinationProcess.get(COUNT_WGB_NOT_NETWORK);

					destinationProcess.put(AVG_DURATION_WGB,(currentDurationMean*count_wgb_not_network + duration)/(count_wgb_not_network+1) );
					destinationProcess.put(COUNT_WGB_NOT_NETWORK, count_wgb_not_network + 1);
					}catch(Exception e){}


					destinationProcess.put(COUNT_WGB, count_wgb + 1);



					double lengthWritten = getLengthFromDetailAnnotation(incomingEdge.getAnnotation(DETAIL));
					double currentLength = destinationProcess.get(TOTAL_LENGTH_WRITTEN);
					destinationProcess.put(TOTAL_LENGTH_WRITTEN, currentLength + lengthWritten);

					HashSet<String> fileGeneratedByProcess = fileWgb.get(ProcessPid);
					String filePath = sourceVertex.getAnnotation(PATH);

					if(filePath != null){

						if(!fileGeneratedByProcess.contains(filePath)){
							fileGeneratedByProcess.add(filePath);
							double countFileGenerated = destinationProcess.get(COUNT_OF_WGB_FILES);
							destinationProcess.put(COUNT_OF_WGB_FILES, countFileGenerated + 1);
						}

						HashSet<String> directoriesWgbByProcess = directoryWgb.get(ProcessPid);
						String directory = getDirectory(filePath);
						if(!directoriesWgbByProcess.contains(directory)){

							directoriesWgbByProcess.add(directory);
							double countDirectoryWgb = destinationProcess.get(COUNT_OF_DIRECTORIES_WGB);
							destinationProcess.put(COUNT_OF_DIRECTORIES_WGB, countDirectoryWgb +1);
						}

					}


					try{

						if(sourceVertex.getAnnotation(CLASS).equals(FILE_SYSTEM)){
							double count_filesystem = destinationProcess.get(COUNT_FILESYSTEM_WGB);
							destinationProcess.put(COUNT_FILESYSTEM_WGB, count_filesystem + 1);

							HashSet<String> extensionWgbByProcess = extensionWgb.get(ProcessPid);
							String extension = getExtension(filePath);

							if(!extensionWgbByProcess.contains(extension)){

								extensionWgbByProcess.add(extension);
								double countExtension = destinationProcess.get(COUNT_EXTENSION_TYPE_WGB);
								destinationProcess.put(COUNT_EXTENSION_TYPE_WGB, countExtension + 1);
							}

							if(extension.equals(EXE) || extension.equals(DLL) || extension.equals(DAT) || extension.equals(BIN)){
								double count_exe_dll = destinationProcess.get(COUNT_EXE_DAT_DLL_BIN_WGB);
								destinationProcess.put(COUNT_EXE_DAT_DLL_BIN_WGB, count_exe_dll + 1);
							}

						}else if(sourceVertex.getAnnotation(CLASS).equals(REGISTRY)){
							double count_registry = destinationProcess.get(COUNT_REGISTRY_WGB);
							destinationProcess.put(COUNT_REGISTRY_WGB, count_registry + 1);
						}

					}catch(Exception e){}

					try{
						if(sourceVertex.getAnnotation(SUBTYPE).equals(NETWORK)){
							double countNetworkSend = destinationProcess.get(COUNT_NETWORK_SEND);
							destinationProcess.put(COUNT_NETWORK_SEND, countNetworkSend + 1);
						}
					}catch(Exception e){}


				}
			}


			}catch(NullPointerException e){
				logger.log(Level.SEVERE,null,e);
			}
			putInNextFilter(incomingEdge);

		}else{
			logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}", new Object[]{
					incomingEdge,
					incomingEdge == null ? null : incomingEdge.getSourceVertex(),
					incomingEdge == null ? null : incomingEdge.getDestinationVertex()
			});
		}
	}


	public static double differenceBetweenTwoTimesWOAmPm(String[] time1,String[] time2){

		double result = 0;
		double secondCarry = 0;
		double minuteCarry = 0;
		double hourCarry = 0;

		// time[0]  = hour, time[1] = minutes, time[2] = seconds, time[3] = 10 nanoseconds, time[4] = AM or PM

		double nanosec1 = Double.parseDouble(time1[3]);
		double nanosec2 = Double.parseDouble(time2[3]);
		if (nanosec1 >= nanosec2){
			result += nanosec1 - nanosec2;
		}else{
			result += SCALE_TIME + nanosec1 - nanosec2;
			secondCarry += 1;
		}

		double second1 = Double.parseDouble(time1[2]);
		double second2 = Double.parseDouble(time2[2]);

		if ((second1-secondCarry) >= second2){
			result += (second1 - secondCarry - second2)*SCALE_TIME;
		}else{
			result += (60 + second1 - secondCarry - second2)*SCALE_TIME;
			minuteCarry += 1;
		}

		double minute1 = Double.parseDouble(time1[1]);
		double minute2 = Double.parseDouble(time2[1]);

		if((minute1 - minuteCarry) >= minute2){
			result += (minute1 - minuteCarry - minute2)*60*SCALE_TIME;
		}else{
			result += (60 + minute1 - minuteCarry - minute2)*60*SCALE_TIME;
			hourCarry += 1;
		}

		double hour1 = Double.parseDouble(time1[0]);
		double hour2 = Double.parseDouble(time2[0]);

		if((hour1 - hourCarry) >= hour2){
			result += (hour1 - hourCarry - hour2)*60*60*SCALE_TIME;
		}else{
			result += (60 + hour1 - hourCarry - hour2)*60*60*SCALE_TIME;
		}


		return result;
	}

	/*
	 * do t1 - t2 and send the result in 10 nanoseconds
	 * we suppose t1 > t2
	 */
	public static double differenceBetweenTwoTimes(String t1,String t2){
		String[] time1 = t1.split(":|\\.| ");
		String[] time2 = t2.split(":|\\.| ");
		double result = 0;
		// time[0]  = hour, time[1] = minutes, time[2] = seconds, time[3] = 10 nanoseconds, time[4] = AM or PM
		if(!time1[4].equals(time2[4])){
			time1[0] =Double.toString(Double.parseDouble(time1[0])+12);
		}
		result = differenceBetweenTwoTimesWOAmPm(time1, time2);

		return result;
	}

	/*
	 * Length sometimes appears in the detail annotation with the form xxx: xxx, Length: 123,123, xxx: ...
	 */
	public static double getLengthFromDetailAnnotation(String detail){
		int result = 0;
		Pattern pattern = Pattern.compile("(Length: (1|2|3|4|5|6|7|8|9|0|,)*)");
		Matcher matcher = pattern.matcher(detail);
		if(matcher.find()){
			String lengthPattern = matcher.group();
			lengthPattern = lengthPattern.substring(8);
			String[] digitDecomposition = lengthPattern.split(",");
			int n = digitDecomposition.length;
			if(digitDecomposition[n-1].equals(" ")){
				n -= 1;
			}
			int powerOfThousand = 1;
			for(int i = n-1; i >= 0; i--){
				result += powerOfThousand*Integer.parseInt(digitDecomposition[i]);
				powerOfThousand *= 1000;
			}
		}


		return (double)result;
	}

	public static String getExtension(String path){
		String result = "";
		String[] pathDecomposition = path.split("\\\\");
		int n = pathDecomposition.length;
		String[] filename = pathDecomposition[n-1].split("\\.");
		if(filename.length >= 2){
			result = filename[filename.length - 1];
		}

		return result;
	}

	/*
	 * get the directory path from the path annotation
	 */
	public static String getDirectory(String path){
		String result = "";
		String[] pathDecomposition = path.split("\\\\");
		int n = pathDecomposition.length;
		if(n <= 1){
			result = path;
		}else{
			result = path.substring(0, path.length()-pathDecomposition[n-1].length());
		}
		return result;
	}

}
