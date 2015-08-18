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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;
import spade.filter.LLVMFilter;

public class FunctionMonitor extends LLVMFilter {

    
    public FunctionMonitor() {
    	super();
    }

    public boolean initialize(String arguments) {
       
	super.initialize(arguments);	           

	String[] tokens = arguments.split("\\s+");    
	try{	
	    BufferedWriter bw = new BufferedWriter(new FileWriter(tokens[2]));
	    for (String key : methodsToMonitor) {
	        bw.write(key + "\n");
 	    }
	    bw.close();
	}
	catch(IOException exception){
	    System.out.println("File IO Error");
	    return false;		
	}	
	
	return true;
    }

    
    public static void main(String args[]) {

        String dotFileName = args[0];
        String functionFileName = args[1];
        String outFileName = args[2];

        String arguments = dotFileName + " " + functionFileName + " " + outFileName;

        FunctionMonitor llvmFilter = new FunctionMonitor();
        llvmFilter.initialize(arguments);
        System.out.println("The Methods to monitor are printed to the file : " + outFileName);
    }

}
