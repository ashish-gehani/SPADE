/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.filter.clamprov;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.filter.ClamProv;
import spade.utility.HelperFunctions;

public class ClamProvThread extends Thread{
	
	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final long sleepMillis;
	private final ClamProvLogReader logReader;
	private final ClamProv filterReference;

	public ClamProvThread(final ClamProvLogReader logReader, final long sleepMillis,
			final ClamProv filterReference) throws Exception{
		this.logReader = logReader;
		this.sleepMillis = sleepMillis;
		this.filterReference = filterReference;
	}

	@Override
	public void run(){
		while(!filterReference.isShutdown()){
			ClamProvEvent event = null;
			try{
				event = logReader.read();
				if(event == null){
					HelperFunctions.sleepSafe(sleepMillis);
					continue;
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Error! Stopped reading clam-prov log '" + logReader.path + "'", e);
				break;
			}
			if(event != null){
				filterReference.handleClamProvEvent(event);
			}
		}
	}
}
