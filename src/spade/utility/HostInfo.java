/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

import java.io.File;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONObject;

import spade.reporter.audit.AuditEventReader;
import spade.reporter.audit.OPMConstants;
import spade.reporter.audit.VertexIdentifier;

/**
 * Class/Subclasses to manage host information for Audit reporter.
 */
public class HostInfo{

	private static final Logger logger = Logger.getLogger(HostInfo.class.getName());
	
	/**
	 * Class to read host information from the underlying operating system.
	 * Currently only for Ubuntu 14.04.
	 */
	public static class ReadFromOperatingSystem{
		
		/**
		 * Creates an instance of Host class by gathering information required from different
		 * utilities.
		 * 
		 * @return Host class instance / NULL (if an error occurs)
		 */
		public static Host read(){
			Host host = new Host();
			String serialNumber = readSerialNumberFromDbusFile();
			if(serialNumber != null){
				host.serialNumber = serialNumber;
				host.hostType = OPMConstants.ARTIFACT_HOST_TYPE_DESKTOP;
				String unameLine = readUnameOutput();
				if(unameLine != null){
					String unameTokens[] = unameLine.split("\\s+");
					host.hostName = unameTokens[1];
					host.operationSystem = unameTokens[3] + " - " + unameTokens[2];
					List<Interface> interfaces = readInterfaces();
					if(interfaces != null){
						host.interfaces = interfaces;
						return host;
					}
				}
			}
			return null;
		}
		
		/**
		 * Creates an instance of Host class by gathering information required from different
		 * utilities. If any of the host information is not found then returns incomplete
		 * Host object instead of null.
		 * 
		 * @return Host class instance
		 */
		public static Host readSafe(){
			String hostName = "", operatingSystem = "";
			String unameLine = readUnameOutput();
			if(unameLine != null){
				String unameTokens[] = unameLine.split("\\s+");
				if(unameTokens.length > 1){
					hostName = unameTokens[1] == null ? "" : unameTokens[1];
				}
				if(unameTokens.length > 3){
					operatingSystem = unameTokens[3] == null ? "" : unameTokens[3];
				}
			}
			List<Interface> interfaces = readInterfaces();
			interfaces = interfaces == null ? new ArrayList<Interface>() : interfaces;
			
			Host host = new Host();
			host.serialNumber = unNullify(readSerialNumberFromDbusFile());
			host.hostType = OPMConstants.ARTIFACT_HOST_TYPE_DESKTOP;
			host.hostName = hostName;
			host.operationSystem = operatingSystem;
			host.interfaces = interfaces;
			return host;
		}
		
		/**
		 * Returns all the 'configured' network interfaces
		 * 
		 * @return list of network interfaces / NULL (in case of error)
		 */
		private static List<Interface> readInterfaces(){
			try{
				List<Interface> interfacesResult = new ArrayList<Interface>();
				
				Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
				LinkedList<NetworkInterface> networkInterfacesList = new LinkedList<NetworkInterface>();
				while(networkInterfaces.hasMoreElements()){
					networkInterfacesList.addLast(networkInterfaces.nextElement());
				}
				
				while(!networkInterfacesList.isEmpty()){
					NetworkInterface networkInterface = networkInterfacesList.removeFirst();
					// Since there can be subinterfaces, adding all to the networkInterfaces list and processing them. 
					// Breadth first traversal.
					Enumeration<NetworkInterface> networkSubInterfaces = networkInterface.getSubInterfaces();
					while(networkSubInterfaces.hasMoreElements()){
						networkInterfacesList.addLast(networkSubInterfaces.nextElement());
					}
					
					Interface interfaceResult = new Interface();
					interfaceResult.name = unNullify(networkInterface.getName());
					interfaceResult.macAddress = parseBytesToMacAddress(networkInterface.getHardwareAddress());
					
					List<String> ips = new ArrayList<String>();
					Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
					while(inetAddresses.hasMoreElements()){
						InetAddress inetAddress = inetAddresses.nextElement();
						byte[] ipBytes = inetAddress.getAddress();
						if(inetAddress instanceof Inet4Address){
							ips.add(parseBytesToIpv4(ipBytes));
						}else if(inetAddress instanceof Inet6Address){
							ips.add(parseBytesToIpv6(ipBytes));
						}else{
							logger.log(Level.WARNING, "Unknown address type: " + inetAddress);
						}
					}
					
					interfaceResult.ips = ips;
					
					interfacesResult.add(interfaceResult);
				}
				
				return interfacesResult;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to poll network interfaces", e);
			}
			return null;
		}
		
		/**
		 * Returns the serial number for the system.
		 * 
		 * NOTE: 'lshw' utility should have the setuid-bit turned on otherwise serial number
		 * won't be returned.
		 * 
		 * @return serial number / NULL (in case of error)
		 */
		@SuppressWarnings("unused")
		private static String readSerialNumberFromLshw(){
			// Only get system class information. 'serial:' keyword otherwise matched incorrectly to other classes.
			String command = "lshw -C system";
			try{
				Execute.Output output = Execute.getOutput(command);
				if(output.hasError()){
					output.log();
					return null;
				}else{
					List<String> stdOutLines = output.getStdOut();
					for(String stdOutLine : stdOutLines){
						if(stdOutLine != null){
							stdOutLine = stdOutLine.trim();
							if(stdOutLine.startsWith("serial:")){
								return stdOutLine.split(":")[1].trim();
							}
						}
					}
					logger.log(Level.SEVERE, "No 'serial' key found in '"+command+"' command.");
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to execute command '"+command+"'", e);
			}
			return null;
		}
		
		/**
		 * Read '/var/lib/dbus/machine-id' file
		 * 
		 * @return null or machine id generated by dbus
		 */
		private static String readSerialNumberFromDbusFile(){
			String filepath = "/var/lib/dbus/machine-id";
			try{
				if(FileUtility.isFileReadable(filepath)){
					try{
						List<String> lines = FileUtility.readLines(filepath);
						if(lines == null || lines.isEmpty()){
							logger.log(Level.SEVERE, "NULL/Empty lines in file: " + filepath);
							return null;
						}else{
							return lines.get(0);
						}
					}catch(Exception e){
						logger.log(Level.SEVERE, "Failed to read file: " + filepath, e);
						return null;
					}
				}else{
					logger.log(Level.SEVERE, "File is not readable: " + filepath);
					return null;
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to check if file is readable: " + filepath, e);
				return null;
			}
		}
		
		/**
		 * Return the output of command: 'uname -a'
		 * 
		 * @return uname output / NULL (in case of error)
		 */
		private static String readUnameOutput(){
			 String command = "uname -a";
			 try{
				 Execute.Output output = Execute.getOutput(command);
				 if(output.hasError()){
					 output.log();
				 }else{
					 List<String> stdOutLines = output.getStdOut();
					 if(stdOutLines.isEmpty()){
						 logger.log(Level.SEVERE, "Empty '"+command+"' output.");
					 }else{
						 return stdOutLines.get(0);
					 }
				 }
			 }catch(Exception e){
				 logger.log(Level.SEVERE, "Failed to execute command '" + command + "'", e);
			 }
			 return null;
		}
		
		/**
		 * Returns the byte value as hex in the format: '00'
		 * 
		 * @param b byte
		 * @return formatted hex string
		 */
		private static String parseByteToHex(byte b){
			return String.format("%02x", parseByteToInt(b));
		}
		
		/**
		 * Returns the byte value as int
		 * 
		 * @param b byte
		 * @return int
		 */
		private static int parseByteToInt(byte b){
			return b & 0xff;
		}
		
		/**
		 * Converts given byte array to a string representation of the mac address
		 * 
		 * @param bytes byte array where length is 6
		 * @return mac address as string
		 */
		private static String parseBytesToMacAddress(byte[] bytes){
			if(bytes == null || bytes.length < 6){
				return "";
			}else{
				return parseByteToHex(bytes[0]) + ":" + parseByteToHex(bytes[1]) + ":" + parseByteToHex(bytes[2])
						+ ":" + parseByteToHex(bytes[3]) + ":" + parseByteToHex(bytes[4]) + ":" + parseByteToHex(bytes[5]);
			}
		}
		
		/**
		 * Converts given byte array to a string representation of ipv4 address
		 * 
		 * @param bytes byte array where the length is 4
		 * @return ipv4 address as string
		 */
		private static String parseBytesToIpv4(byte[] bytes){
			if(bytes == null || bytes.length < 4){
				return "";
			}else{
				return parseByteToInt(bytes[0]) + "." + parseByteToInt(bytes[1]) + "." + parseByteToInt(bytes[2]) + "."
						+ parseByteToInt(bytes[3]);
			}
		}
		
		/**
		 * Converts given byte array to a string representation of ipv6 address
		 * 
		 * @param bytes byte array where the length is 16
		 * @return ipv6 address as string
		 */
		private static String parseBytesToIpv6(byte[] bytes){
			if(bytes == null || bytes.length < 16){
				return "";
			}else{
				return parseByteToHex(bytes[0]) + parseByteToHex(bytes[1]) + ":"
						+ parseByteToHex(bytes[2]) + parseByteToHex(bytes[3]) + ":"
						+ parseByteToHex(bytes[4]) + parseByteToHex(bytes[5]) + ":"
						+ parseByteToHex(bytes[6]) + parseByteToHex(bytes[7]) + ":"
						+ parseByteToHex(bytes[8]) + parseByteToHex(bytes[9]) + ":"
						+ parseByteToHex(bytes[10]) + parseByteToHex(bytes[11]) + ":"
						+ parseByteToHex(bytes[12]) + parseByteToHex(bytes[13]) + ":"
						+ parseByteToHex(bytes[14]) + parseByteToHex(bytes[15]);
			}
		}
	}
	
	/**
	 * Class to read key values from a given file and convert that to a Host class instance
	 */
	public static class ReadFromFile{
		
		/**
		 * Reads the given file which should contain all the keys required by the host object
		 * 
		 * NOTE: Any changes to read format should be updated accordingly in the WriteToFile class
		 * 
		 * @param filePath path of the file which contains host information
		 * @return Host class instance
		 */
		public static Host read(String filePath){
			try{
				Map<String, String> map = FileUtility.readConfigFileAsKeyValueMap(filePath, "=");
				return Host.mapToHost(map);
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to read host from file: " + filePath, e);
				return null;
			}
		}
		
		/**
		 * Reads the given file which should DOESN'T have to contain all the keys required by the host object
		 * 
		 * NOTE: Any changes to read format should be updated accordingly in the WriteToFile class
		 * 
		 * @param filePath path of the file which contains host information
		 * @return Host class instance
		 */
		public static Host readSafe(String filePath){
			Map<String, String> map = null;
			try{
				map = FileUtility.readConfigFileAsKeyValueMap(filePath, "=");
			}catch(Exception e){
				// ignore exception because we don't care. Can't return null.
			}
			if(map == null){
				return new Host();
			}else{
				return Host.mapToHost(map);
			}
		}
		
	}
	
	/**
	 * Class to write the host object to file as key values.
	 */
	public static class WriteToFile{
		
		/**
		 * Writes the host object to the given file (overwrites the file)
		 * 
		 * NOTE: Any changes to writing format should be updated accordingly in the ReadFromFile class
		 * 
		 * @param host host instance
		 * @param filepath file to write to
		 * @return true (success) / false (failure)
		 */
		public static boolean write(Host host, String filepath){
			PrintWriter writer = null;
			try{
				writer = new PrintWriter(new File(filepath));
				Map<String, String> map = host.getAnnotationsMap();
				for(Map.Entry<String, String> entry : map.entrySet()){
					writer.println(entry.getKey()+"="+entry.getValue());
				}
				return true;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to write host to file: " + filepath, e);
				return false;
			}finally{
				if(writer != null){
					try{ writer.close(); }catch(Exception e){}
				}
			}
		}
		
	}
	
	/**
	 * Class to parse the host object out of the audit log that is gotten.
	 */
	public static class ReadFromAuditLog{
		
		/**
		 * Returns the host object as read from the audit record
		 * 
		 * @param auditRecordMsg the part of the audit record which contains "spade_host_msg:[<msg>]"
		 * @return Host instance / NULL (if expected format different)
		 */
		public static Host read(String auditRecordMsg){
			if(auditRecordMsg == null){
				logger.log(Level.SEVERE, "NULL audit record");
			}else{
				int msgStartIndex = auditRecordMsg.indexOf(AuditEventReader.USER_MSG_SPADE_AUDIT_HOST_KEY+":");
				if(msgStartIndex < 0){
					logger.log(Level.SEVERE, "Not an audit record with host info: " + auditRecordMsg);
				}else{
					int valueStartIndex = auditRecordMsg.indexOf('[', msgStartIndex);
					int valueEndIndex = auditRecordMsg.indexOf(']', valueStartIndex+1);
					if(valueStartIndex > -1 && valueEndIndex > -1){
						String jsonObjectStringHex = auditRecordMsg.substring(valueStartIndex+1, valueEndIndex);
						Host host = Host.hexStringToHost(jsonObjectStringHex);
						return host;
					}else{
						logger.log(Level.SEVERE, "Malformed host info audit record: " + auditRecordMsg);
					}
				}
			}
			return null;
		}
	}
	
	/**
	 * Class to write the host object to the audit log
	 */
	public static class WriteToAuditLog{
		
		private static String auditRecordMsgFormat = AuditEventReader.USER_MSG_SPADE_AUDIT_HOST_KEY + ":[%s]";
		
		/**
		 * Converts the host object to string format and writes to the audit log
		 * 
		 * @param host Host object to write to audit log
		 * @return true (success) / false (failure)
		 */
		public static boolean write(Host host){
			String hostAsString = Host.hostToHexString(host);
			String auditRecordMsg = String.format(auditRecordMsgFormat, hostAsString);
			try{
				Execute.Output output = Execute.getOutput("auditctl -m " + auditRecordMsg);
				output.log();
				if(!output.hasError()){
					return true;
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to emit host record.", e);
			}
			return false;
		}
		
	}
	
	/**
	 * Convenience function to convert a null string to empty string otherwise use the string as is.
	 * 
	 * @param string any string
	 * @return empty string or original string
	 */
	private static String unNullify(String string){
		return string == null ? "" : string;
	}
	
	/**
	 * Convenience function to convert a list to a comma-separated string.
	 * Empty list converted to empty string.
	 * 
	 * @param list list of string
	 * @return string
	 */
	private static String listToString(List<String> list){
		String string = "";
		for(String element : list){
			string += element + ",";
		}
		if(string.length() > 0){
			string = string.substring(0, string.length() - 1);
		}
		return string;
	}
	
	/**
	 * Convenience function to convert a comma separated string to a list of string.
	 * 
	 * @param string comma-separated string
	 * @return list of string
	 */
	private static List<String> stringToList(String string){
		String[] elements = string.split(",");
		List<String> list = new ArrayList<String>(Arrays.asList(elements));
		return list;
	}
	
	/**
	 * A class which contains all the Host information required at the moment.
	 */
	public static class Host implements VertexIdentifier{
		
		private static final long serialVersionUID = -5990891430891823447L;
		// Everything initialized to empty values to avoid NPE
		private String serialNumber = "", hostType = "", hostName = "", operationSystem = "";
		private List<Interface> interfaces = new ArrayList<Interface>();
		
		@Override
		public Map<String, String> getAnnotationsMap() {
			return hostToMap(this);
		}
		
		@Override
		public String toString() {
			return "Host [serialNumber=" + serialNumber + ", hostType=" + hostType + ", hostName=" + hostName
					+ ", operationSystem=" + operationSystem + ", interfaces=" + interfaces + "]";
		}

		private static String hostToHexString(Host host){
			Map<String, String> map = host.getAnnotationsMap();
			JSONObject jsonObject = new JSONObject(map);
			String jsonObjectString = jsonObject.toString();
			String jsonHexObjectString = CommonFunctions.encodeHex(jsonObjectString);
			return jsonHexObjectString;
		}
		
		private static Host hexStringToHost(String objectAsString){
			String jsonObjectString = CommonFunctions.decodeHex(objectAsString);
			try{
				@SuppressWarnings("unchecked")
				Map<String, String> map = (TreeMap<String, String>)new ObjectMapper().readValue(
						jsonObjectString, TreeMap.class);
				Host host = mapToHost(map);
				return host;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to parse host info from audit record", e);
				return null;
			}
		}
		
		private static Map<String, String> hostToMap(Host host){
			Map<String, String> map = new HashMap<String, String>();
			map.put(OPMConstants.ARTIFACT_HOST_NETWORK_NAME, unNullify(host.hostName));
			map.put(OPMConstants.ARTIFACT_HOST_TYPE, unNullify(host.hostType));
			map.put(OPMConstants.ARTIFACT_HOST_SERIAL_NUMBER, unNullify(host.serialNumber));
			map.put(OPMConstants.ARTIFACT_HOST_OPERATING_SYSTEM, unNullify(host.operationSystem));
			int interfacesCount = 0;
			if(host.interfaces != null){
				interfacesCount = host.interfaces.size();
			}
			map.put(OPMConstants.ARTIFACT_HOST_INTERFACES_COUNT, String.valueOf(interfacesCount));
			for(int a = 0; a<host.interfaces.size(); a++){
				String interfaceNameKey = OPMConstants.buildHostNetworkInterfaceNameKey(a);
				String interfaceMacAddressKey = OPMConstants.buildHostNetworkInterfaceMacAddressKey(a);
				String interfaceIpAddressKey = OPMConstants.buildHostNetworkInterfaceIpAddressesKey(a);
				Interface i = host.interfaces.get(a);
				map.put(interfaceNameKey, unNullify(i.name));
				map.put(interfaceMacAddressKey, unNullify(i.macAddress));
				String ipString = listToString(i.ips);
				map.put(interfaceIpAddressKey, ipString);
			}
			return map;
		}
		
		private static Host mapToHost(Map<String, String> map){
			Host host = new Host();
			host.hostName = unNullify(map.get(OPMConstants.ARTIFACT_HOST_NETWORK_NAME));
			host.hostType = unNullify(map.get(OPMConstants.ARTIFACT_HOST_TYPE));
			host.serialNumber = unNullify(map.get(OPMConstants.ARTIFACT_HOST_SERIAL_NUMBER));
			host.operationSystem = unNullify(map.get(OPMConstants.ARTIFACT_HOST_OPERATING_SYSTEM));
			Integer a = CommonFunctions.parseInt(unNullify(map.get(OPMConstants.ARTIFACT_HOST_INTERFACES_COUNT)), 0);
			for(int b = 0; b < a; b++){
				String interfaceNameKey = OPMConstants.buildHostNetworkInterfaceNameKey(b);
				String interfaceMacAddressKey = OPMConstants.buildHostNetworkInterfaceMacAddressKey(b);
				String interfaceIpAddressKey = OPMConstants.buildHostNetworkInterfaceIpAddressesKey(b);
				
				Interface i = new Interface();
				i.name = unNullify(map.get(interfaceNameKey));
				i.macAddress = unNullify(map.get(interfaceMacAddressKey));
				i.ips = stringToList(unNullify(map.get(interfaceIpAddressKey)));
				host.interfaces.add(i);
			}
			return host;
		}

	}
	
	/**
	 * Class which contains the network interface information required at the moment.
	 */
	private static class Interface{
		// Everything initialized to empty values to avoid NPE
		private String name = "", macAddress = "";
		private List<String> ips = new ArrayList<String>();
		@Override
		public String toString() {
			return "Interface [name=" + name + ", macAddress=" + macAddress + ", ips=" + ips + "]";
		}
	}
	
	/**
	 * Reads the current host information from the operating system and writes that to the 
	 * specified file.
	 * 
	 * @param filePath file to write to
	 * @return true/false
	 */
	public static boolean generateCurrentHostFile(String filePath){
		try{
			Host currentHost = HostInfo.ReadFromOperatingSystem.readSafe();
			if(currentHost != null){
				return HostInfo.WriteToFile.write(currentHost, filePath);
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to generate current Host file", e);
		}
		return false;
	}
	
	/**
	 * Reads the host information from the given file and prints to standard out.
	 * 
	 * @param filePath file to read from
	 * @return true/false
	 */
	private static boolean printHostFile(String filePath){
		try{
			Host host = HostInfo.ReadFromFile.readSafe(filePath);
			if(host != null){
				System.out.println(host);
				return true;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to check Host file", e);
		}
		return false;
	}
	
	public static void main(String [] args){
		int exitValue = 0;
		if(args.length != 2){
			exitValue = -1;
		}else{
			String option = args[0];
			String filepath = args[1];
			if("-o".equals(option)){
				if(generateCurrentHostFile(filepath)){
					exitValue = 0;
				}else{
					exitValue = -1;
				}
			}else if("-p".equals(option)){
				if(printHostFile(filepath)){
					exitValue = 0;
				}else{
					exitValue = -1;
				}
			}else{
				exitValue = -1;
			}
		}
		System.exit(exitValue);
	}
}
