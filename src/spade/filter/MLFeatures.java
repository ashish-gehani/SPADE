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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;


/**
 * A filter to compute features for apt classification.
 *
 * Arguments format: nameOfOutputFile nameOfMalwareBinaryRunning.
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
	private HashMap<String,String> firstActivityTime = new HashMap<>();
	private HashMap<String,String> firstActivityDate = new HashMap<>();
	private HashMap<String,String> lastUsed = new HashMap<>();
	private HashMap<String,String> lastWgb = new HashMap<>();
	private HashMap<Integer,Integer> ancestors = new HashMap<>();
	private HashMap<String,HashSet<String>> fileUsed = new HashMap<>();
	private HashMap<String,HashSet<String>> fileWgb = new HashMap<>();
	private HashMap<String,HashSet<String>> extensionUsed = new HashMap<>();
	private HashMap<String,HashSet<String>> extensionWgb = new HashMap<>();
	private HashMap<String,HashSet<String>> directoryUsed = new HashMap<>();
	private HashMap<String,HashSet<String>> directoryWgb = new HashMap<>();
	private HashMap<String,HashMap<Integer,HashSet<String>>> taintPropagation = new HashMap<>();
	private HashMap<Integer,Double> labelMap = new HashMap<>();
	private HashMap<Integer,String> status = new HashMap<>();
	private HashMap<String,Integer> processIdentifierToHashcode = new HashMap<>();
	//private HashSet<String> fileThatDontTaint = new HashSet<>(Arrays.asList("HKLM\\System\\CurrentControlSet\\Control\\Session Manager","HKU\\.DEFAULT",
	//	"HKLM\\System\\CurrentControlSet\\Control\\ComputerName\\ActiveComputerName","HKLM\\System\\CurrentControlSet\\Control\\Lsa\\FipsAlgorithmPolicy",
	//"HKLM\\System\\CurrentControlSet\\Control\\Lsa","HKLM\\System\\CurrentControlSet\\Control\\Nls\\Sorting\\Versions"));
	//private HashMap<String,HashSet<String>> usedDatetime = new HashMap<>();
	//private HashMap<String,HashSet<String>> wgbDatetime = new HashMap<>();
	//private HashMap<String,HashSet<String>> usedDatetimeWoMeta = new HashMap<>();
	//private HashMap<String,HashSet<String>> wgbDatetimeWoMeta = new HashMap<>();
	private HashMap<String,HashSet<String>> wtbName = new HashMap<>();
	private HashMap<String,String> ppidMap = new HashMap<>();
	private HashMap<String,HashSet<String>> fileSystemWgb = new HashMap<>();
	private HashMap<String,HashSet<String>> remoteServers = new HashMap<>();
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
	private final String SUBTYPE = "subtype";
	private final String NETWORK = "network";
	private final String DURATION = "duration";
	private final String COMMANDLINE = "commandline";
	private final String REGSETINFOKEY = "RegSetInfoKey";
	private final String REGSETVALUE = "RegSetValue";
	private final String OPERATION = "operation";
	private final String CATEGORY = "category"; 
	private final String DATETIME = "datetime";
	private final String PPID = "ppid";
	private final String READ = "Read";
	private final String WRITE = "Write";
	private final String REMOTE_HOST = "remote host";
	///////////////////////////////////////////////
	private final String USED = "Used";
	private final String WCB = "WasControlledBy";
	private final String WTB = "WasTriggeredBy";
	private final String WGB = "WasGeneratedBy";
	//////////////////////////////////////////////
	private static double SCALE_TIME = 10000000;
	private static double BEGINNING_THRESHOLD = SCALE_TIME;
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
	private final String COUNT_NETWORK_SEND = "countNetworkSend";
	private final String COUNT_OF_DIRECTORIES_USED = "countOfDirectoriesUsed";
	private final String COUNT_OF_DIRECTORIES_WGB = "countOfDirectoriesWgb";
    private final String COUNT_USED_BEGINNING = "countUsedBeginning";
    private final String COUNT_WGB_BEGINNING = "countWgbBeginning";
    private final String IS_NEW = "isNew";
    private final String COUNT_REG_SET_INFO_KEY = "countRegSetInfoKey";
    private final String COUNT_REG_SET_VALUE = "countRegSetValue";
    private final String TOTAL_LENGTH_READ_BEGINNING = "totalLengthReadBeginning";
    private final String TOTAL_LENGTH_WRITTEN_BEGINNING = "totalLengthWrittenBeginning";
    private final String COUNT_OF_USED_FILES_BEGINNING = "countOfUsedFilesBegining";
    private final String COUNT_OF_WGB_FILES_BEGINNING = "countOfWgbFilesBegining";
	private final String COUNT_FILESYSTEM_USED_BEGINNING = "countFilesystemUsedBeginning";
	private final String COUNT_FILESYSTEM_WGB_BEGINNING = "countFilesystemWgbBeginning";
	private final String COUNT_REGISTRY_USED_BEGINNING = "countRegistryUsedBeginning";
	private final String COUNT_REGISTRY_WGB_BEGINNING = "countRegistryWgbBeginning";
	private final String COUNT_EXTENSION_TYPE_USED_BEGINNING = "countExtensionTypeUsedBeginning";
	private final String COUNT_EXTENSION_TYPE_WGB_BEGINNING = "countExtensionTypeWgbBeginning";
	private final String COUNT_EXE_DAT_DLL_BIN_USED_BEGINNING = "countExeDatDllBinUsedBeginning";
	private final String COUNT_EXE_DAT_DLL_BIN_WGB_BEGINNING = "countExeDatDllBinWgbBeginning";
	private final String COUNT_NETWORK_RECEIVE_BEGINNING = "countNetworkReceiveBeginning";
	private final String COUNT_NETWORK_SEND_BEGINNING = "countNetworkSendBeginning";
	private final String COUNT_OF_DIRECTORIES_USED_BEGINNING = "countOfDirectoriesUsedBeginning";
	private final String COUNT_OF_DIRECTORIES_WGB_BEGINNING = "countOfDirectoriesWgbBeginning";
	private final String WRITES_THEN_EXECTUTES = "writeThenExecutes";
	private final String COUNT_REMOTE_HOST = "countRemoteHost";
	private final String COUNT_THREAD = "countThread";



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
	/**
	*This method is invoked by the kernel when shutting down a filter.
	*It can output 3 different kind of file. The first one which is name filepathFeatures.csv corresponds to the file containing the learning features
	*that is needed to complete the classification task.
	*The second file that is output is filepathFeatures.csv_ . Each line of this file corresponds to a file that has been written by bad processes (would be empty for benign run).
	*and for each file we get the number of processes that got tainted by reading this file. It was useful to create the vocabulary of words generated by malware.
	*The third file is used to plot the histogram of activity of a process. For each process it records all the dates and times ofthe used and wgb edges connected to this process.
	*Its use can slow down the data collection.
	* @return True if the reporter was shut down successfully.
	*/
	@Override
	public boolean shutdown(){
		Set<String> names = new HashSet<String>(Arrays.asList(COUNT_USED,COUNT_WGB,AVG_DURATION_BETWEEN_TWO_USED,AVG_DURATION_BETWEEN_TWO_WGB,AVG_DURATION_USED,
				AVG_DURATION_WGB,COUNT_FILESYSTEM_USED,COUNT_FILESYSTEM_WGB,LIFE_DURATION,TOTAL_LENGTH_READ,TOTAL_LENGTH_WRITTEN,COUNT_OF_USED_FILES,
				COUNT_OF_WGB_FILES,COUNT_EXTENSION_TYPE_USED,COUNT_EXTENSION_TYPE_WGB,COUNT_EXE_DAT_DLL_BIN_USED,COUNT_EXE_DAT_DLL_BIN_WGB,COUNT_WTB,COUNT_REGISTRY_USED,
				COUNT_REGISTRY_WGB,COUNT_NETWORK_RECEIVE,COUNT_NETWORK_SEND,COUNT_OF_DIRECTORIES_USED,COUNT_OF_DIRECTORIES_WGB,COUNT_USED_BEGINNING,COUNT_WGB_BEGINNING,IS_NEW,COUNT_REG_SET_INFO_KEY,
				COUNT_REG_SET_VALUE,TOTAL_LENGTH_READ_BEGINNING,TOTAL_LENGTH_WRITTEN_BEGINNING,COUNT_OF_USED_FILES_BEGINNING,COUNT_OF_WGB_FILES_BEGINNING,
				COUNT_FILESYSTEM_USED_BEGINNING,COUNT_FILESYSTEM_WGB_BEGINNING,COUNT_REGISTRY_USED_BEGINNING,COUNT_REGISTRY_WGB_BEGINNING,COUNT_EXTENSION_TYPE_USED_BEGINNING,
				COUNT_EXTENSION_TYPE_WGB_BEGINNING,COUNT_EXE_DAT_DLL_BIN_USED_BEGINNING,COUNT_EXE_DAT_DLL_BIN_WGB_BEGINNING,COUNT_NETWORK_RECEIVE_BEGINNING,COUNT_NETWORK_SEND_BEGINNING,
				COUNT_OF_DIRECTORIES_USED_BEGINNING,COUNT_OF_DIRECTORIES_WGB_BEGINNING,WRITES_THEN_EXECTUTES,COUNT_REMOTE_HOST,COUNT_THREAD));

		try (Writer writer = new FileWriter(filepathFeatures)) {

		   for (String name : names){
			   writer.append(name)
			   		 .append(',');
		   }
		   writer.append(USER)
		   		 .append(',')
		   		 .append(NAME)
		   		 .append(',')
		   		 .append(COMMANDLINE)
		   		 .append(',')
		   		 .append(PROCESS_IDENTIFIER)
		   		 .append(',')
		   		 .append(PPID)
		   		 .append(',')
				 .append(LABEL)
		   		 .append(',')
				 .append("state")
				 .append(',')
				 .append("NameTriggered")
				 .append(',')
				 .append("listOfUsedFiles")
				 .append(',')
				 .append("listOfWgbFiles")
		   		 .append(eol);
		   for (String key : features.keySet()) {

			   HashMap<String,Double> current = features.get(key);

			   for(String column : names  ){
				   writer.append(Double.toString(current.get(column)))
		              	 .append(',');
			   }
			   writer.append(agentsName.get(key))
			   		 .append(',')
			   		 .append(processName.get(key))
			   		 .append(',')
			   		 .append(processCommandline.get(key).replace(',', ' '))
			   		 .append(',')
			   		 .append(key)
			   		 .append(',')
			   		 .append(ppidMap.get(key))
			   		 .append(',')
			   		 .append(Double.toString(labelMap.get(processIdentifierToHashcode.get(key))))
			   		 .append(',')
			   		 .append(status.get(processIdentifierToHashcode.get(key)))
			   		 .append(',')
			   		 .append(wtbName.get(key).toString().replace(',',';').trim())
			   		 .append(',')
			   		 .append(fileUsed.get(key).toString().replace(',', ' ').substring(1,fileUsed.get(key).toString().length()-1).trim())
			   		 .append(',')
			   		 .append(fileWgb.get(key).toString().replace(',', ' ').substring(1,fileWgb.get(key).toString().length()-1).trim())
			         .append(eol);
		   }
		} catch (IOException ex) {
		  ex.printStackTrace(System.err);
		}

		try (Writer writer = new FileWriter(filepathFeatures+"_")) {


			   writer.append("FileName")
			   		 .append(',')
			   		 .append("CountTaintedProcesses")
			   		 .append(',')
			   		 .append("OperationTypes")
			   		 .append(',')
			   		 .append("taintedProcesses")
			   		 .append(eol);
			   for (String key : taintPropagation.keySet()) {

				   HashSet<String> processes = taintPropagation.get(key).get(0);
				   HashSet<String> WgbOperation = taintPropagation.get(key).get(1);

				   writer.append(key)
				   		 .append(',')
				   		 .append(Integer.toString(processes.size()))
				   		 .append(',')
				   		 .append(WgbOperation.toString().replace(',', ' '))
				   		 .append(',')
				   		 .append(processes.toString().replace(',', ' '))
				         .append(eol);
			   }
			} catch (IOException ex) {
			  ex.printStackTrace(System.err);
			}
		
		/*
		 * When adding the commented line refering to usedDatetime, WgbDatetime, UsedDatetimeWoMeta and WgbDatetimeWoMeta
		 * in the code, the following line permit to create a file that will be used to plot the histogram of activity
		 * thanks to HistogramActivity.py
		 */
		/*try (Writer writer = new FileWriter(filepathFeatures+"__")) {


			   writer.append("Pid")
			   		 .append(',')
			   		 .append("UsedDatetime")
			   		 .append(',')
			   		 .append("WgbDatetime")
			   		 .append(',')
			   		 .append("UsedDatetimeWoMeta")
			   		 .append(',')
			   		 .append("WgbDatetimeWoMeta")
			   		 .append(eol);
			   for (String key : usedDatetime.keySet()) {

				   HashSet<String> usedDatetimeList = usedDatetime.get(key);
				   HashSet<String> WgbDatetimeList = wgbDatetime.get(key);
				   HashSet<String> usedDatetimeListWoMeta = usedDatetimeWoMeta.get(key);
				   HashSet<String> WgbDatetimeListWoMeta = wgbDatetimeWoMeta.get(key);

				   writer.append(key)
				   		 .append(',')
				   		 .append(usedDatetimeList.toString().replace(',', ';'))
				   		 .append(',')
				   		 .append(WgbDatetimeList.toString().replace(',', ';'))
				   		 .append(',')
				   		 .append(usedDatetimeListWoMeta.toString().replace(',', ';'))
				   		 .append(',')
				   		 .append(WgbDatetimeListWoMeta.toString().replace(',', ';'))
				         .append(eol);
			   }
			} catch (IOException ex) {
			  ex.printStackTrace(System.err);
			}*/

		return true;
	}
	/**
	 *This method is called when the filter receives a vertex.
	 *This is where the maps that contains the feature of what will be used to create feature are initialized
	 * 
	 * @param @incomingVertex The vertex received by this filter.
	 */

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
				initialFeatures.put(COUNT_USED_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_WGB_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(IS_NEW, INITIAL_ZERO);
				initialFeatures.put(COUNT_REG_SET_INFO_KEY, INITIAL_ZERO);
				initialFeatures.put(COUNT_REG_SET_VALUE, INITIAL_ZERO);
				initialFeatures.put(TOTAL_LENGTH_READ_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(TOTAL_LENGTH_WRITTEN_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_USED_FILES_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_WGB_FILES_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_FILESYSTEM_USED_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_FILESYSTEM_WGB_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_REGISTRY_USED_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_REGISTRY_WGB_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXTENSION_TYPE_USED_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXTENSION_TYPE_WGB_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXE_DAT_DLL_BIN_USED_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_EXE_DAT_DLL_BIN_WGB_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_NETWORK_RECEIVE_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_NETWORK_SEND_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_DIRECTORIES_USED_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(COUNT_OF_DIRECTORIES_WGB_BEGINNING, INITIAL_ZERO);
				initialFeatures.put(WRITES_THEN_EXECTUTES, INITIAL_ZERO);
				initialFeatures.put(COUNT_REMOTE_HOST, INITIAL_ZERO);
				initialFeatures.put(COUNT_THREAD, INITIAL_ZERO);
				features.put(processPid,initialFeatures);
				//usedDatetime.put(processPid, new HashSet<>());
				//wgbDatetime.put(processPid, new HashSet<>());
				//usedDatetimeWoMeta.put(processPid, new HashSet<>());
				//wgbDatetimeWoMeta.put(processPid, new HashSet<>());
				wtbName.put(processPid, new HashSet<>());
				ppidMap.put(processPid, incomingVertex.getAnnotation(PPID));

				remoteServers.put(processPid, new HashSet<>());

				fileUsed.put(processPid, new HashSet<>());
				fileWgb.put(processPid, new HashSet<>());
				fileSystemWgb.put(processPid, new HashSet<>());
				extensionUsed.put(processPid, new HashSet<>());
				extensionWgb.put(processPid, new HashSet<>());
				directoryUsed.put(processPid, new HashSet<>());
				directoryWgb.put(processPid, new HashSet<>());
				processName.put(processPid, incomingVertex.getAnnotation(NAME));
				processCommandline.put(processPid, incomingVertex.getAnnotation(COMMANDLINE));
				processIdentifierToHashcode.put(processPid, incomingVertex.hashCode());
				if((maliciousName != null) && (incomingVertex.getAnnotation(NAME).equals(maliciousName))){
					labelMap.put(incomingVertex.hashCode(), BAD);
					status.put(incomingVertex.hashCode(), MALICIOUS);
				}else{
					labelMap.put(incomingVertex.hashCode(), GOOD);
					status.put(incomingVertex.hashCode(), BENIGN);
				}
			}else{
				labelMap.put(incomingVertex.hashCode(), GOOD);
				status.put(incomingVertex.hashCode(), BENIGN);
			}
			ancestors.put(incomingVertex.hashCode(), 0);
			putInNextFilter(incomingVertex);
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null && incomingEdge.getSourceVertex() != null && incomingEdge.getDestinationVertex() != null){

			try{

			if(!status.get(incomingEdge.getDestinationVertex().hashCode()).equals(BENIGN)){
				if(status.get(incomingEdge.getSourceVertex().hashCode()).equals(BENIGN)){
					//String testFilePath = incomingEdge.getDestinationVertex().getAnnotation(PATH) ;
					//if((testFilePath == null)||(!fileThatDontTaint.contains(testFilePath))){
					String testClass = incomingEdge.getDestinationVertex().getAnnotation(CLASS) ;
					if((testClass == null)||(!testClass.equals(REGISTRY))){
						status.put(incomingEdge.getSourceVertex().hashCode(), TAINTED);
					}

				}
			}



			if(labelMap.get(incomingEdge.getSourceVertex().hashCode()) < 1.0 ){
				int countAncestors = ancestors.get(incomingEdge.getSourceVertex().hashCode());
				double currentLabel = labelMap.get(incomingEdge.getSourceVertex().hashCode());
				if(labelMap.get(incomingEdge.getDestinationVertex().hashCode())>0.5){
					labelMap.put(incomingEdge.getSourceVertex().hashCode(),(currentLabel*countAncestors + 5*labelMap.get(incomingEdge.getDestinationVertex().hashCode()))/(countAncestors+5));
					ancestors.put(incomingEdge.getSourceVertex().hashCode(), countAncestors+5);
				}else{
					labelMap.put(incomingEdge.getSourceVertex().hashCode(),(currentLabel*countAncestors + labelMap.get(incomingEdge.getDestinationVertex().hashCode()))/(countAncestors+1));
					ancestors.put(incomingEdge.getSourceVertex().hashCode(), countAncestors+1);
				}
			}
			if (incomingEdge.getSourceVertex().type().equals(PROCESS)) {

				AbstractVertex sourceProcessVertex = incomingEdge.getSourceVertex();
				AbstractVertex destinationVertex = incomingEdge.getDestinationVertex();
				String ProcessPid = sourceProcessVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String, Double> sourceProcess = features.get(ProcessPid);

				if (incomingEdge.type().equals(USED)){

					String time = incomingEdge.getAnnotation(TIME);
					String datetime = incomingEdge.getAnnotation(DATETIME);
					
					double count_used = sourceProcess.get(COUNT_USED);

					if(!firstActivityTime.containsKey(ProcessPid)){
						firstActivityTime.put(ProcessPid, time);
						firstActivityDate.put(ProcessPid, datetime);
					}
					//some previous log didn't have datetime annotations
					if(datetime == null){
						sourceProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(time,firstActivityTime.get(ProcessPid)));
					}else{
						sourceProcess.put(LIFE_DURATION, differenceBetweenTwoDates(datetime,firstActivityDate.get(ProcessPid),time,firstActivityTime.get(ProcessPid)));
					}
					

					if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
						addToCount(COUNT_USED_BEGINNING, sourceProcess);
					}
					//used to create the files that are used to print the histogram of activity of a process
					//HashSet<String> usedDatetimeList = usedDatetime.get(ProcessPid);
					//usedDatetimeList.add(getDate(incomingEdge.getAnnotation(DATETIME))+" "+time);
					//usedDatetime.put(ProcessPid,usedDatetimeList);

					//if(READ.equals(incomingEdge.getAnnotation(CATEGORY))){
						//HashSet<String> usedDatetimeListWoMeta = usedDatetimeWoMeta.get(ProcessPid);
						//usedDatetimeListWoMeta.add(getDate(incomingEdge.getAnnotation(DATETIME))+" "+time);
						//usedDatetimeWoMeta.put(ProcessPid,usedDatetimeListWoMeta);
					//}


					if (count_used != 0){
						double avgDurationBetweenTwoUsed = sourceProcess.get(AVG_DURATION_BETWEEN_TWO_USED);
						String lastTimeUsed = lastUsed.get(ProcessPid);
						sourceProcess.put(AVG_DURATION_BETWEEN_TWO_USED,(avgDurationBetweenTwoUsed*(count_used-1) + differenceBetweenTwoTimes(time, lastTimeUsed))/count_used );
					}
					lastUsed.put(ProcessPid, time);





					//not used anymore
					try{
					double duration = Double.parseDouble(incomingEdge.getAnnotation(DURATION));
					double currentDurationMean = sourceProcess.get(AVG_DURATION_USED);
					double count_used_not_network = sourceProcess.get(COUNT_USED_NOT_NETWORK);

					sourceProcess.put(AVG_DURATION_USED,(currentDurationMean*count_used_not_network + duration)/(count_used_not_network+1) );
					sourceProcess.put(COUNT_USED_NOT_NETWORK, count_used_not_network + 1);
					}catch(Exception e){System.err.println("Error duration" + e);}


					addToCount(COUNT_USED, sourceProcess);




					double lengthRead = getLengthFromDetailAnnotation(incomingEdge.getAnnotation(DETAIL));
					double currentLength = sourceProcess.get(TOTAL_LENGTH_READ);
					sourceProcess.put(TOTAL_LENGTH_READ, currentLength + lengthRead);
					if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
						double currentLengthBeginning = sourceProcess.get(TOTAL_LENGTH_READ_BEGINNING);
						sourceProcess.put(TOTAL_LENGTH_READ_BEGINNING, currentLengthBeginning + lengthRead);
					}

					HashSet<String> fileUsedByProcess = fileUsed.get(ProcessPid);
					String filePath = destinationVertex.getAnnotation(PATH);
					if(filePath != null){

						if(taintPropagation.containsKey(filePath)){
							HashMap<Integer,HashSet<String>> taintInfo = taintPropagation.get(filePath);
							HashSet<String> taintProcesses = taintInfo.get(0);
							taintProcesses.add(ProcessPid);
							taintInfo.put(0, taintProcesses);
							taintPropagation.put(filePath, taintInfo);
						}


						if(!fileUsedByProcess.contains(filePath)){

							fileUsedByProcess.add(filePath);
							addToCount(COUNT_OF_USED_FILES, sourceProcess);
							if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_OF_USED_FILES_BEGINNING, sourceProcess);
							}

						}

						HashSet<String> directoriesUsedByProcess = directoryUsed.get(ProcessPid);
						String directory = getDirectory(filePath);
						if(!directoriesUsedByProcess.contains(directory)){

							directoriesUsedByProcess.add(directory);
							addToCount(COUNT_OF_DIRECTORIES_USED, sourceProcess);
							if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_OF_DIRECTORIES_USED_BEGINNING, sourceProcess);
							}
						}
					}

					if(FILE_SYSTEM.equals(destinationVertex.getAnnotation(CLASS))){
						addToCount(COUNT_FILESYSTEM_USED, sourceProcess);
						if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
							addToCount(COUNT_FILESYSTEM_USED_BEGINNING, sourceProcess);
						}
						HashSet<String> extensionUsedByProcess = extensionUsed.get(ProcessPid);
						String extension = getExtension(filePath);

						if(!extensionUsedByProcess.contains(extension)){

							extensionUsedByProcess.add(extension);
							addToCount(COUNT_EXTENSION_TYPE_USED, sourceProcess);
							if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_EXTENSION_TYPE_USED_BEGINNING, sourceProcess);
							}
						}

						if(EXE.equals(extension) || DLL.equals(extension) || DAT.equals(extension) || BIN.equals(extension)){
							addToCount(COUNT_EXE_DAT_DLL_BIN_USED, sourceProcess);
							if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_EXE_DAT_DLL_BIN_USED_BEGINNING, sourceProcess);
							}
						}

					}else if(REGISTRY.equals(destinationVertex.getAnnotation(CLASS))){
						addToCount(COUNT_REGISTRY_USED, sourceProcess);
						if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
							addToCount(COUNT_REGISTRY_USED_BEGINNING, sourceProcess);
						}
					}


					if(NETWORK.equals(destinationVertex.getAnnotation(SUBTYPE))){
						addToCount(COUNT_NETWORK_RECEIVE, sourceProcess);
						if(sourceProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
							addToCount(COUNT_NETWORK_RECEIVE_BEGINNING, sourceProcess);
						}

						HashSet<String> remoteHost = remoteServers.get(ProcessPid);
						remoteHost.add(destinationVertex.getAnnotation(REMOTE_HOST));
						sourceProcess.put(COUNT_REMOTE_HOST, (double)remoteHost.size());

					}



				}else if (incomingEdge.type().equals(WCB)){

					agentsName.put(ProcessPid,destinationVertex.getAnnotation(USER));

				}else if(incomingEdge.type().equals(WTB)){
					
					String time = incomingEdge.getAnnotation(TIME);
					String datetime = incomingEdge.getAnnotation(DATETIME);
					String destinationPid = destinationVertex.getAnnotation(PROCESS_IDENTIFIER);
					HashMap<String,Double> destinationProcess = features.get(destinationPid);

					HashSet<String> wtbNameSet = wtbName.get(destinationPid);
					if(destinationPid!=ProcessPid){
						wtbNameSet.add(sourceProcessVertex.getAnnotation(NAME));
						wtbName.put(destinationPid, wtbNameSet);
						addToCount(COUNT_WTB, destinationProcess);
					}else{
						addToCount(COUNT_THREAD, destinationProcess);
					}

					sourceProcess.put(IS_NEW,1.0);

					


					double doesWritesAndExecutes = destinationProcess.get(WRITES_THEN_EXECTUTES);
					double currentWritesExecutes = (fileSystemWgb.get(destinationPid).contains(
							sourceProcessVertex.getAnnotation(COMMANDLINE).split(" ")[0])) ? 1.0 : 0.0;
					if((currentWritesExecutes + doesWritesAndExecutes) >= 1.0){
						destinationProcess.put(WRITES_THEN_EXECTUTES,1.0);
					}


					if(labelMap.get(incomingEdge.getDestinationVertex().hashCode()) == BAD){
						labelMap.put(incomingEdge.getSourceVertex().hashCode(),BAD);
					}

					if(status.get(incomingEdge.getDestinationVertex().hashCode()).equals(MALICIOUS)){
						status.put(incomingEdge.getSourceVertex().hashCode(),MALICIOUS);
					}

					if(!firstActivityTime.containsKey(destinationPid)){
						firstActivityTime.put(destinationPid, time);
						firstActivityDate.put(destinationPid, datetime);
					}
					
					if(datetime == null){
						destinationProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(time,firstActivityTime.get(destinationPid)));
					}else{
						destinationProcess.put(LIFE_DURATION, differenceBetweenTwoDates(datetime,firstActivityDate.get(destinationPid),time,firstActivityTime.get(destinationPid)));
					}
				}





			}else if (incomingEdge.getDestinationVertex().type().equals(PROCESS)){

				AbstractVertex destinationProcessVertex = incomingEdge.getDestinationVertex();
				AbstractVertex sourceVertex = incomingEdge.getSourceVertex();
				String ProcessPid = destinationProcessVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String, Double> destinationProcess = features.get(ProcessPid);

				String time = incomingEdge.getAnnotation(TIME);
				String datetime = incomingEdge.getAnnotation(DATETIME);

				if(!firstActivityTime.containsKey(ProcessPid)){
					firstActivityTime.put(ProcessPid, time);
					firstActivityDate.put(ProcessPid, datetime);
				}
				
				//to deal with the different log that contain the date attribut or not
				if(datetime == null){
					destinationProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(time,firstActivityTime.get(ProcessPid)));
				}else{
					destinationProcess.put(LIFE_DURATION, differenceBetweenTwoDates(datetime,firstActivityDate.get(ProcessPid),time,firstActivityTime.get(ProcessPid)));
				}


				if (incomingEdge.type().equals(WGB)){



					double count_wgb = destinationProcess.get(COUNT_WGB);

					if (count_wgb != 0){
						double avgDurationBetweenTwoWgb = destinationProcess.get(AVG_DURATION_BETWEEN_TWO_WGB);
						String lastTimeWgb = lastWgb.get(ProcessPid);
						destinationProcess.put(AVG_DURATION_BETWEEN_TWO_WGB,(avgDurationBetweenTwoWgb*(count_wgb-1) + differenceBetweenTwoTimes(time, lastTimeWgb))/count_wgb );

					}
					lastWgb.put(ProcessPid, time);

					if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
						addToCount(COUNT_WGB_BEGINNING, destinationProcess);
					}

					//HashSet<String> wgbDatetimeList = wgbDatetime.get(ProcessPid);
					//wgbDatetimeList.add(getDate(incomingEdge.getAnnotation(DATETIME))+" "+time);
					//wgbDatetime.put(ProcessPid, wgbDatetimeList);

					//if(WRITE.equals(incomingEdge.getAnnotation(CATEGORY))){
					//	HashSet<String> wgbDatetimeListWoMeta = wgbDatetimeWoMeta.get(ProcessPid);
					//	wgbDatetimeListWoMeta.add(getDate(incomingEdge.getAnnotation(DATETIME))+" "+time);
					//	wgbDatetimeWoMeta.put(ProcessPid,wgbDatetimeListWoMeta);
					//}

					try{
					double duration = Double.parseDouble(incomingEdge.getAnnotation(DURATION));
					double currentDurationMean = destinationProcess.get(AVG_DURATION_WGB);
					double count_wgb_not_network = destinationProcess.get(COUNT_WGB_NOT_NETWORK);

					destinationProcess.put(AVG_DURATION_WGB,(currentDurationMean*count_wgb_not_network + duration)/(count_wgb_not_network+1) );
					destinationProcess.put(COUNT_WGB_NOT_NETWORK, count_wgb_not_network + 1);
					}catch(Exception e){System.err.println("Error duration" + e);}


					addToCount(COUNT_WGB, destinationProcess);



					double lengthWritten = getLengthFromDetailAnnotation(incomingEdge.getAnnotation(DETAIL));
					double currentLength = destinationProcess.get(TOTAL_LENGTH_WRITTEN);
					destinationProcess.put(TOTAL_LENGTH_WRITTEN, currentLength + lengthWritten);
					if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
						double currentLengthBeginning = destinationProcess.get(TOTAL_LENGTH_WRITTEN_BEGINNING);
						destinationProcess.put(TOTAL_LENGTH_WRITTEN_BEGINNING, currentLengthBeginning + lengthWritten);
					}

					HashSet<String> fileGeneratedByProcess = fileWgb.get(ProcessPid);

					String filePath = sourceVertex.getAnnotation(PATH);

					if(filePath != null){

						if((labelMap.get(destinationProcessVertex.hashCode()) == 1.0)){
							if(!taintPropagation.containsKey(filePath)){
								HashMap<Integer,HashSet<String>> taintInfo = new HashMap<>();
								taintInfo.put(0,new HashSet<>());
								HashSet<String> WgbOperation = new HashSet<>();
								WgbOperation.add(incomingEdge.getAnnotation(OPERATION));
								taintInfo.put(1, WgbOperation);
								taintPropagation.put(filePath,taintInfo);
							}else{
								HashMap<Integer,HashSet<String>> taintInfo = taintPropagation.get(filePath);
								HashSet<String> WgbOperation = taintInfo.get(1) ;
								WgbOperation.add(incomingEdge.getAnnotation(OPERATION));
								taintInfo.put(1, WgbOperation);
								taintPropagation.put(filePath, taintInfo);


							}

						}

						if(!fileGeneratedByProcess.contains(filePath)){

							fileGeneratedByProcess.add(filePath);

							addToCount(COUNT_OF_WGB_FILES, destinationProcess);
							if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_OF_WGB_FILES_BEGINNING, destinationProcess);
							}

						}





						HashSet<String> directoriesWgbByProcess = directoryWgb.get(ProcessPid);
						String directory = getDirectory(filePath);
						if(!directoriesWgbByProcess.contains(directory)){

							directoriesWgbByProcess.add(directory);
							addToCount(COUNT_OF_DIRECTORIES_WGB, destinationProcess);
							if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_OF_DIRECTORIES_WGB_BEGINNING, destinationProcess);
							}
						}

					}




					if(FILE_SYSTEM.equals(sourceVertex.getAnnotation(CLASS))){
						addToCount(COUNT_FILESYSTEM_WGB, destinationProcess);
						if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
							addToCount(COUNT_FILESYSTEM_WGB_BEGINNING, destinationProcess);
						}



						HashSet<String> extensionWgbByProcess = extensionWgb.get(ProcessPid);
						String extension = getExtension(filePath);
						
						if(!extensionWgbByProcess.contains(extension)){
							
							extensionWgbByProcess.add(extension);
							addToCount(COUNT_EXTENSION_TYPE_WGB, destinationProcess);
							if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_EXTENSION_TYPE_WGB_BEGINNING, destinationProcess);
							}
						}
						if(EXE.equals(extension)){
							HashSet<String> fileSystemGeneratedByProcess = fileSystemWgb.get(ProcessPid);
							if(!fileSystemGeneratedByProcess.contains(filePath)){
								
								fileSystemGeneratedByProcess.add(filePath);

							}
						}

						if(EXE.equals(extension) || DLL.equals(extension) || DAT.equals(extension) || BIN.equals(extension)){
							addToCount(COUNT_EXE_DAT_DLL_BIN_WGB, destinationProcess);
							if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
								addToCount(COUNT_EXE_DAT_DLL_BIN_WGB_BEGINNING, destinationProcess);
							}
						}

					}else if(REGISTRY.equals(sourceVertex.getAnnotation(CLASS))){
						addToCount(COUNT_REGISTRY_WGB, destinationProcess);
						if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
							addToCount(COUNT_REGISTRY_WGB_BEGINNING, destinationProcess);
						}

						if(incomingEdge.getAnnotation(OPERATION).equals(REGSETINFOKEY)){
							addToCount(COUNT_REG_SET_INFO_KEY, destinationProcess);
						}
						if(incomingEdge.getAnnotation(OPERATION).equals(REGSETVALUE)){
							addToCount(COUNT_REG_SET_VALUE,destinationProcess);
						}
					}



					if(NETWORK.equals(sourceVertex.getAnnotation(SUBTYPE))){
						addToCount(COUNT_NETWORK_SEND, destinationProcess);
						if(destinationProcess.get(LIFE_DURATION) < BEGINNING_THRESHOLD){
							addToCount(COUNT_NETWORK_SEND_BEGINNING, destinationProcess);
						}

						HashSet<String> remoteHost = remoteServers.get(ProcessPid);
						remoteHost.add(sourceVertex.getAnnotation(REMOTE_HOST));
						destinationProcess.put(COUNT_REMOTE_HOST, (double)remoteHost.size());
					}



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

/**
 * method that increments the value of featureName in the map processFeature
 * @param featureName the name of the feature you want to increment in the map
 * @param processFeature the map that contains the feature you want to increment
 */
	public static void addToCount(String featureName,HashMap<String,Double> processFeature){
		double currentCount = processFeature.get(featureName);
		processFeature.put(featureName, currentCount +1);
	}

/**
 * difference of time annotations without taking the AM/PM
 * @param time1
 * @param time2
 * @return the time difference between time1 and time2(time1 is supposed to by larger than time2). Unit = 10 nanosecond
 */
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

/**
 * difference of time annotations taking the AM/PM
 * @param t1
 * @param t2
 * @return the time difference between time1 and time2(time1 is supposed to by larger than time2). Unit = 10 nanosecond
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

/**
 * Length sometimes appears in the detail annotation with the form xxx: xxx, Length: 123,123, xxx: ...
 * @param detail annotation
 * @return the value of the length present in the detail annotation if it was present, 0 otherwise
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
/**
 * Get the extension of a file from the path annotation
 * @param path
 * @return extension present in the path
 */
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

/**
 * 
 * @param path
 * @return Get the directory path from the path
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

/**
 * The datetime contain the dateand the time (time has a second precision)
 * @param datetime
 * @return the date in format mm/dd/yyyy
 */
	
	public static String getDate(String datetime){
		String date = "";
		if(datetime != null){
			date = datetime.split(" ")[0];
		}
		return date;
	}

	
/**
 * assume that d1 and d2 are the datetime annotation of edge1 and edge2
 * and t1 and t2 are the time annotation of edge1 and edge2 
 * and assume that edge1 take place after edge2.
 * Need to use datetime and time annotation because time is datetime annotation is not precise enough
 * @param d1 datetime annotation
 * @param d2 datetime annotation
 * @param t1 time annotation
 * @param t2 time annotation
 * @return difference between the two datetime date of d1, t1 and date of d2 t2. Unit = 10 nanosecond
 */
	public static double differenceBetweenTwoDates(String d1,String d2,String t1,String t2){
		String[] datetime1 = d1.split(" ");
		String[] datetime2 = d2.split(" ");
		double oneDay = 24*60*60*SCALE_TIME;
		String date1 = datetime1[0];
		String date2 = datetime2[0];
		
		SimpleDateFormat ft = new SimpleDateFormat ("MM/dd/yyyy"); 
	    Date dateFormat1;
	    Date dateFormat2;
	    double differenceDate = 0.0;
	    try {
	       dateFormat1 = ft.parse(date1); 
	       dateFormat2 = ft.parse(date2);
	       differenceDate =  (double)(dateFormat1.getTime() - dateFormat2.getTime())*10000; 
	    }catch (ParseException e) { 
	       System.out.println("Unparseable using " + ft); 
	    }
		
		String[] time1 = t1.split(":|\\.| ");
		String[] time2 = t2.split(":|\\.| ");
		double result = 0;
		// time[0]  = hour, time[1] = minutes, time[2] = seconds, time[3] = 10 nanoseconds, time[4] = AM or PM
		if(time1[4].equals(time2[4])){
			if(differenceDate > 0 ){
				time1[0] =Double.toString(Double.parseDouble(time1[0])+24);
				differenceDate -= oneDay;
			}
			result = differenceBetweenTwoTimesWOAmPm(time1, time2) + differenceDate;
		}else if(time1[4].equals("PM")){
			time1[0] =Double.toString(Double.parseDouble(time1[0])+12);
			result = differenceBetweenTwoTimesWOAmPm(time1, time2) + differenceDate;
		}else{
			time1[0] =Double.toString(Double.parseDouble(time1[0])+12);
			result = differenceBetweenTwoTimesWOAmPm(time1, time2) + (differenceDate-oneDay);
		}
		
		

		return result;
	}
	
}
