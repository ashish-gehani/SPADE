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
 
 USAGE: java NetfilterDelayStatistics
 IMPORTANT: audit logs should be in /var/log/audit/ and should be named in the format "audit.log.n", being n a number or just "audit.log" for n = 0
 
 This tool reads log lines from the audit system and performs the following actions:
 * calculates the mean delay (in terms of logs records) between SOCKADDR records from connect syscalls and the first NETFILTER_PKT record corresponding to the same connection
 * calculates the variance of those delays
 * saves a mapping of delays to their respective number of occurences in a file named "delayMap"
 * performs the previous three actions for TCP and UDP connections in a separate way (with respective files "TCPdelayMap" and "UDPdelayMap"
 */
package spade.utility;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Carolina de Senne Garcia
 */
public class NetfilterDelayStatistics {

	/* ---------------------- GLOBAL VARIABLES ---------------------- */

	/* String patter for SYSCALL CONNECT line */
	private static final String syscallConnectLine = "^type=SYSCALL msg=audit\\((.+)\\)(?:.*)syscall=42(?:.*)$";
	private static final String syscallLineExample = "type=SYSCALL msg=audit(1496447381.681:6005304): arch=c000003e syscall=42 success=yes exit=0 a0=5 a1=7f9f8ca59680 a2=6e a3=7f9f8ca54f20 items=1 ppid=3356 pid=4172 auid=1001 uid=0 gid=1001 euid=0 suid=0 fsuid=0 egid=1001 sgid=1001 fsgid=1001 tty=pts0 ses=3 key=(null)";

	/* String pattern for SOCKADDR line*/
	private static final String sockaddrLine = "^type=SOCKADDR msg=audit\\((.+)\\)(?:.+)saddr=(.+)$";
	private static final String sockaddrLineExample = "type=SOCKADDR msg=audit(1494523111.293:1661796): saddr=01002F686F6D652F6361726F6C2F2E676E7570672F532E6770672D6167656E74";

	/* String pattern for NETFILTER_PKT line*/
	private static final String netfilterPktLine = "^type=NETFILTER_PKT(?:.+)?daddr=(\\d+\\.\\d+\\.\\d+\\.\\d+)(?:.+)?dport=(\\d+)$";
	private static final String netfilterPktLineExample = "type=NETFILTER_PKT msg=audit(1494469680.359:1606788): action=0 hook=1 len=76 inif=enp0s3 outif=? smac=52:54:00:12:35:02 dmac=08:00:27:1d:e8:ec macproto=0x0800 saddr=91.189.89.199 daddr=10.0.2.15 ipid=31284 proto=17 sport=123 dport=58139";

	/* String with the path to log file */
	private static final String logPath = "/var/log/audit/audit.log";

	/* Map to keep track of processed SOCKADDR records (IP:port)  and their respective line number in the log file*/
	private static Map<String, Integer> sockaddrToLineNumber = new HashMap<String,Integer>();

	/* Map to keep track of processed TCP SOCKADDR records (IP:port)  and their respective line number in the log file*/
	private static Map<String, Integer> TCPsockaddrToLineNumber = new HashMap<String,Integer>();

	/* Map to keep track of processed UDP SOCKADDR records (IP:port)  and their respective line number in the log file*/
	private static Map<String, Integer> UDPsockaddrToLineNumber = new HashMap<String,Integer>();

	/* Set to keep track of SYSCALL CONNECT timestamps, to identify SOCKADDR that correspond to TCP */
	private static Set<String> timestampsFound = new HashSet<String>();

	/* Map from the delays found (in terms of records or lines) between SOCKADDR and first respective NETFILTER and number of occurences */
	private static Map<Integer, Integer> delayOccurences = new HashMap<Integer,Integer>();

	/* Map from the delays found (in terms of records or lines) between SOCKADDR and first respective NETFILTER and number of occurences for TCP connections*/
	private static Map<Integer, Integer> TCPdelayOccurences = new HashMap<Integer,Integer>();
	
	/* Map from the delays found (in terms of records or lines) between SOCKADDR and first respective NETFILTER and number of occurences for UDP connections*/
	private static Map<Integer, Integer> UDPdelayOccurences = new HashMap<Integer,Integer>();

	/* Map to keep track of NETFILTER packets and their respective line numbers in the log file */
	private static Map<String, Integer> netfilterToLineNumber = new HashMap<String,Integer>();

	/* Counts the number of occurences of a netfilter that comes before a syscall */
	private static int countNetfilterBefore = 0;

	/* ----------------------- FUNCTIONS --------------------------- */

	public static void main(String[] args) {
		
		// Patterns
		Pattern syscallP = Pattern.compile(syscallConnectLine);
		Pattern saddrP = Pattern.compile(sockaddrLine);
		Pattern netfilterP = Pattern.compile(netfilterPktLine);

		// Pattern Tests
		//System.out.println(parseLineToAddressString(netfilterPktLineExample, saddrP, netfilterP));
		//System.out.println(parseLineToAddressString(sockaddrLineExample, saddrP, netfilterP));
		//System.out.println(parseLineToTimeStamp(syscallLineExample, syscallP, saddrP));
		//System.out.println(parseLineToTimeStamp(sockaddrLineExample, syscallP, saddrP));

		// Process Files
		ProcessAllLogFiles(syscallP,saddrP,netfilterP);

		// Print Delay Map to File
		printDelayMap("delayMap",0);
		printDelayMap("TCPdelayMap",1);
		printDelayMap("UDPdelayMap",2);

		// Number of netfilters before sockaddr
		System.out.println("Netfilter packets before corresponding sockaddr packet: "+countNetfilterBefore);

		// Calculate mean and variance for delays
		double[] statistics = calculateStatistics();
		System.out.println("Mean = "+statistics[0]+"\nVariance= "+statistics[1]);

	}

	/**
	 * Opens file and saves the delayOccurence map corresponding to type in the file
	 * type can be: 0 (for both UDP and TCP)
	 * 				1 (for TCP)
	 *				2 (for UDP)
	 * @param filename of the file to where should write delay content
	 * @param type of the delayMap that should be recorded (0,1 or 2)
	 *
	 */
	public static void printDelayMap(String filename, int type) {
		BufferedWriter bw;
		try {
			bw = new BufferedWriter(new FileWriter(filename));
			printDelayMap(bw,type);
			bw.close();
			System.out.println("Saved file: "+filename);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.err.println("Error opening delay file");
		}
	}

	/**
	 * Calculate the delay mean and variance
	 *
	 * @return double array with the mean in the first case and variance in the seconde one
	 */
	public static double[] calculateStatistics() {
		int n = 0;
		double mean = 0;
		double variance = 0;
		// calculate mean
		for(Map.Entry<Integer,Integer> entry: delayOccurences.entrySet()) {
			int weight = entry.getValue();
			int delay = entry.getKey();
			n = n+weight;
			mean = mean+(weight*delay);
		}
		mean = mean/n;
		// calculate variance
		for(Map.Entry<Integer,Integer> entry: delayOccurences.entrySet()) {
			int weight = entry.getValue();
			int delay = entry.getKey();
			variance = variance+(weight*(delay-mean)*(delay-mean));
		}
		variance = variance/n;
		double[] statistics = new double[2];
		statistics[0] = mean;
		statistics[1] = variance;
		return statistics;
	}

	/**
	 * Print the Delay Map in the following format:
	 * key : value
	 * 
	 * @param bw BufferedWriter that points to the file to be written
	 * @param type of the Map (TCP = 1, UDP = 2 or both = 0) to be recorded
	 *
	 */
	public static void printDelayMap(BufferedWriter bw, int type) {
		Map<Integer,Integer> map = null;
		if(type == 0) { // both types
			map = delayOccurences;
		} else if(type == 1) { // TCP
			map = TCPdelayOccurences;
		} else if(type == 2) { // UDP
			map = UDPdelayOccurences;
		} else {
			System.err.println("Bad delayMap type!");
			return;
		}
		String delayMap = "";
		for(Map.Entry<Integer,Integer> entry: map.entrySet())
			delayMap += Integer.toString(entry.getKey())+" : "+Integer.toString(entry.getValue())+"\n";
		try {
			bw.write(delayMap);
		} catch(IOException e) {
			System.err.println(e.getMessage());
			System.err.println("Problem writing delay map to file");
		}
	}

	/**
	 * Opens all log files from the oldest to the newest and send them to be processed in ProcessLogFile
	 *
	 * @param saddrP SOCKADDR record pattern
	 * @param netfilterP NETFILTER record patter
	 */
	public static void ProcessAllLogFiles(Pattern syscallP, Pattern saddrP, Pattern netfilterP) {
		// Find the oldest log file
		int count =1;
		boolean end = false;
		while(!end) {
			File f = new File(logPath+"."+Integer.toString(count));
			if(f.exists()) {
				count++;
			} else {
				end = true;
			}
		}
		// send log files in the right order to be processed
		BufferedReader buffer = null;
		int countLine = 1;
		for(int i = count-1; i > 0; i--) {
			System.out.println("Processing "+logPath+"."+i+"...");
			buffer = openFile(logPath+"."+Integer.toString(i));
			countLine = ProcessLogFile(syscallP,saddrP,netfilterP,buffer,countLine);
		}
		System.out.println("Processing "+logPath+"...");
		buffer = openFile(logPath);
		ProcessLogFile(syscallP,saddrP,netfilterP,buffer,countLine);
	}

	/**
	 * Read lines from log file n buffer and process each SOCKADDR and NETFILTER records 
	 * 
	 * @param saddrP SOCKADDR record pattern
	 * @param netfilterP NETFILTER record pattern 
	 * @param buffer from where lines should be read
	 */
	public static int ProcessLogFile(Pattern syscallP, Pattern saddrP, Pattern netfilterP, BufferedReader buffer, int countLine) {
		String line;
		String address_port;
		String timeStamp;
		int protocol = -1;	// 0 = TCP
						   	// 1 = UDP
						   	// -1 not defined
		try {
			while((line = buffer.readLine()) != null) {
				if((timeStamp = parseLineToTimeStamp(line,syscallP,saddrP)) != null) {
					if(line.startsWith("type=SYSCALL")) {
						timestampsFound.add(timeStamp);
					} else if(line.startsWith("type=SOCKADDR")) {
						if(timestampsFound.remove(timeStamp)) {
							protocol = 0; 
						} else {
							protocol = 1;
						}	
					}
				}
				if((address_port = parseLineToAddressString(line,saddrP,netfilterP)) != null) {
					if(line.startsWith("type=SOCKADDR")) {
						treatSockaddrLine(address_port,countLine,protocol);
					} else if(line.startsWith("type=NETFILTER_PKT")) {
						treatNetfilterLine(address_port,countLine);
					}
				}
				countLine++;
			}
		} catch(IOException e) {
			System.err.println(e.getMessage());
			System.err.println("Problem reading file");
		}
		return countLine;
	}

	/**
	 * Insert the pair \\<IP:Port, Line Number\\> in the Mapping of SOCKADDR records found until the current line
	 *
	 * @param addr string containing the IP address and port of the saddr record
	 * @param currentLine line number of the record in the log file
	 */
	public static void treatSockaddrLine(String addr, int currentLine, int protocol) {
		if(netfilterToLineNumber.remove(addr) != null) {
			countNetfilterBefore++;
			return;
		}
		sockaddrToLineNumber.put(addr,currentLine);
		if(protocol == 0) { // TCP case
			TCPsockaddrToLineNumber.put(addr,currentLine);
		} else if(protocol == 1) { // UDP case
			UDPsockaddrToLineNumber.put(addr,currentLine);
		}
	}

	/**
	 * Check if a corresponding SOCKADDR record has been seen in the log file (checks the sockaddrToLineNumber map):
	 *
	 * If it EXISTS in the map, removes it and increment the corresponding delay Occurences number; 
	 * the delay is defined to be the records/lines number delay between a SOCKADDR record and the first corresponding NETFILTER record in the file
	 *
	 * @param addr string containing the IP address and port of the saddr record
	 * @param currentLine line number of the record in the log file
	 */
	public static void treatNetfilterLine(String addr, int currentLine) {
		Integer previousLine = null;
		Integer protocolPreviousLine = null;
		if((previousLine = sockaddrToLineNumber.remove(addr)) != null) {
			Integer delay = currentLine-previousLine;
			Integer Occurences = delayOccurences.remove(delay);
			if(Occurences == null)
				Occurences = new Integer(0);
			delayOccurences.put(delay,++Occurences);
			if((protocolPreviousLine = TCPsockaddrToLineNumber.remove(addr)) != null) {
				if(protocolPreviousLine.equals(previousLine)) {
					Occurences = TCPdelayOccurences.remove(delay);
					if(Occurences == null)
						Occurences = new Integer(0);
					TCPdelayOccurences.put(delay,++Occurences);
				}
			} else if((protocolPreviousLine = UDPsockaddrToLineNumber.remove(addr)) != null) {
				if(protocolPreviousLine.equals(previousLine)) {
					Occurences = UDPdelayOccurences.remove(delay);
					if(Occurences == null)
						Occurences = new Integer(0);
					UDPdelayOccurences.put(delay,++Occurences);
				}
			}
		} else {
				netfilterToLineNumber.put(addr,currentLine);
		}
	}

	/**
	 * Return either null or a String containing the IP address and the port in the following format:
	 * 
	 * xxxxxxxx:xxxx
	 * corresponding to
	 * IP:port
	 * where x is an hexadecimal digit
	 *
	 * If line is a SOCKADDR record, then the addres will be retrieved from saddr
	 * If line is a NETFILTER_PKT record, then the address will be retrieved from daddr and dport
	 * If line is any other type of record, then return null
	 *
	 * @param line the record line being parsed
	 * @param saddrP SOCKADDR record pattern
	 * @param netfilterP NETFILTER_PKT record pattern
	 * @return a string containing the destination IP and ports of a connection or null string
	 */
	public static String parseLineToAddressString(String line, Pattern saddrP, Pattern  netfilterP) {
		Matcher sockaddM = saddrP.matcher(line);
		Matcher netfilterM = netfilterP.matcher(line);
		if(sockaddM.find()) {
			String saddr = sockaddM.group(2);
			if(saddr.length() >= 15)
				return saddr.substring(8,16)+":"+saddr.substring(4,8);
		} else if(netfilterM.find()) {
			String res = "";
			String[] IPfields = netfilterM.group(1).split("\\.");
			for(int i=0; i<IPfields.length; i++) {
				res = res+String.format("%02X",Integer.valueOf(IPfields[i]));
			}
			return res+":"+String.format("%04X",Integer.valueOf(netfilterM.group(2)));
		}
		return null;
	}

	/**
	 * Return either null or a String containing the timestamp of the record (SYSCALL or SOCKADDR)
	 *
	 * @param line the record line being parsed
	 * @param syscallP SYSCALL CONNECT (42) record pattern
	 * @param saddrP SOCKADDR record pattern
	 * @return a string containing the timestamp of the record in line
	 */
	public static String parseLineToTimeStamp(String line, Pattern syscallP, Pattern saddrP) {
		Matcher syscallM = syscallP.matcher(line);
		Matcher sockaddM = saddrP.matcher(line);
		if(syscallM.find()) {
			return syscallM.group(1);
		} else if(sockaddM.find()) {
			return sockaddM.group(1);
		}
		return null;
	}


	/**
	 * Open a file and returns its respective BufferReader
	 *
	 * @param path to file to be opened
	 * @return buffer to read the log file explicited in path
	 */
	public static BufferedReader openFile(String path) {
		FileReader fr = null;
		try {
			fr = new FileReader(path);
		} catch(IOException e) {
			System.err.println(e.getMessage());
			System.err.println("Couldn't open the log file: "+path);
			return null;
		}
		return new BufferedReader(fr);
	}
}
