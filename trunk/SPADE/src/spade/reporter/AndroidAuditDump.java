package spade.reporter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;




public class AndroidAuditDump extends AndroidAudit {

	private String filePath;

	@Override
	public boolean launch(String arguments) {
		filePath = arguments;
		startParsing();
		return true;
	}

	@Override
	public void startParsing()
	{
		try {
			Runnable eventThread = new Runnable() {

				public void run() {
					try {
						eventReader = new BufferedReader(new FileReader(filePath));
						String line;
						while ((line = eventReader.readLine()) != null) {
							processInputLine(line);
						}
					} catch (Exception exception) {
						Logger.getLogger(LinuxAuditDump.class.getName()).log(Level.SEVERE, null, exception);
					}
				}
			};
			new Thread(eventThread).start();
		} catch (Exception exception) {
			Logger.getLogger(LinuxAuditDump.class.getName()).log(Level.SEVERE, null, exception);
		}
	}

	@Override
	public boolean shutdown() {
		// TODO: Close file
		return true;
	}

}
