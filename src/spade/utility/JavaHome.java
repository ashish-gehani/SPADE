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
package spade.utility;

import java.io.File;

public class JavaHome{

	// Prints the path to 'include' directory in java home
    public static void main(String[] arguments) throws Exception{
    	boolean found = false;
    	String javaHomePath = System.getProperty("java.home");
    	File javaHomeFile = new File(javaHomePath);
    	File javaHomeDir = javaHomeFile;
    	do{
    		for(File file : javaHomeDir.listFiles()){
	    		if(file.getName().equals("include")){
	    			System.out.println(file.getAbsolutePath());
	    			found = true;
	    			break;
	    		}
	    	}
    	}while((javaHomeDir = javaHomeDir.getParentFile()) != null && !found);
    }
}
