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

 /*
 This file open log/latest_log_file and prints all log messages
 */
package spade.utility;

import java.io.File;
import java.io.FileFilter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

public class LogParser {

	public static File lastFileModified(String dir) {
	    File fl = new File(dir);
	    File[] files = fl.listFiles(new FileFilter() {          
	        public boolean accept(File file) {
	            return file.isFile();
	        }
	    });
	    long lastMod = Long.MIN_VALUE;
	    File choice = null;
	    for (File file : files) {
	        if (file.lastModified() > lastMod) {
	            choice = file;
	            lastMod = file.lastModified();
	        }
	    }
	    return choice;
	}

	public static void parserXML(File file) {

		try {

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);
			doc.getDocumentElement().normalize();
			NodeList nList = doc.getElementsByTagName("record");
 
			for (int temp = 0; temp < nList.getLength(); temp++) {
		 
				Node nNode = nList.item(temp);
		 	 
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		 
					Element eElement = (Element) nNode;

					String date = eElement.getElementsByTagName("date").item(0).getTextContent();
					String millis = eElement.getElementsByTagName("millis").item(0).getTextContent();
					String logger = eElement.getElementsByTagName("logger").item(0).getTextContent();
					String level = eElement.getElementsByTagName("level").item(0).getTextContent();
					String message = eElement.getElementsByTagName("message").item(0).getTextContent();

					System.out.println(logger+":"+level+":"+date+":"+millis+":"+message);
		 
				}
			}


		} catch (Exception e) {
			e.printStackTrace();
    	}	
	}

    public static void main(String[] arguments) {
        File lastfile = lastFileModified("log");
        if (lastfile == null) {
        	System.out.println("No Log file found");
        }
        if (lastfile.toString().indexOf("SPADE_") == -1) {
        	System.out.println("Can not find a log file: ");
        	return;
        }

       	System.out.println("Paring file: " + lastfile.toString());

        parserXML(lastfile);
    }
}
