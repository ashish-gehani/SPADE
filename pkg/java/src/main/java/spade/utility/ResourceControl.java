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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class ResourceControl {

	public static Long getMaxMemory() {

		// if max memory is set by providing -Xmx argument
        if (Runtime.getRuntime().maxMemory() != Long.MAX_VALUE) {
        	return Runtime.getRuntime().maxMemory();
        }

        Long maxMem=Long.MAX_VALUE;
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File("/proc/meminfo")));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.contains("MemTotal")) {
                    maxMem = Long.parseLong(line.split("\\s")[1])*1024;
                }
            }
            br.close();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return maxMem;
	}

	public static void memoryControlSync() {
		/*
		* This is a synchronized function that waits till java memory stabilizes.
		* This function is called in memory exhausting tasks.
		*/

        Long maxMem = getMaxMemory();
        Long totalMen = Runtime.getRuntime().totalMemory();
        Long freeMem = Runtime.getRuntime().freeMemory();

        try {
	        while (freeMem < 0.2*totalMen) {

	        	System.runFinalization();
	        	System.gc();
	            Thread.sleep(5000); // make it dynamic
	        	maxMem = getMaxMemory();
		        totalMen = Runtime.getRuntime().totalMemory();
		        freeMem = Runtime.getRuntime().freeMemory();

	        }
	    } catch (InterruptedException exception) {
	    	exception.printStackTrace();
	    }

	}

}
