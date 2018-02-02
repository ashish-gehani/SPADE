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

import spade.filter.LLVMFilter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

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
	    System.err.println("Error while writing file with list of functions to monitor.");
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
        System.out.println("The functions to monitor are printed to the file : " + outFileName);
    }

}
