/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2019 SRI International

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
package spade.utility.map.external.screen;

import spade.utility.profile.ReportingArgument;

/**
 * Profiled screen with instrumentation for measuring time for add, contains, remove.
 *
 * @param <K>
 */
public class ProfiledScreen<K> implements Screen<K>{

	private final ScreenProfile profile;
	private final Screen<K> screen;
	
	protected ProfiledScreen(Screen<K> screen, ReportingArgument reportingArgument){
		this.screen = screen;
		
		final String id = reportingArgument.id;
		final long millis = reportingArgument.intervalMillis;
		
		profile = new ScreenProfile(id, millis);
	}
	
	@Override
	public void add(K key){
		try{
			profile.addStart();
			screen.add(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.addStop();
		}
	}

	@Override
	public boolean contains(K key){
		try{
			profile.containsStart();
			return screen.contains(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.containsStop();
		}
	}

	@Override
	public boolean remove(K key){
		try{
			profile.removeStart();
			return screen.remove(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.removeStop();
		}
	}

	@Override
	public void clear(){
		screen.clear();
	}

	@Override
	public void close() throws Exception{
		try{
			profile.stopAll();
		}catch(Exception e){
			
		}
		try{
			screen.close();
		}catch(Exception e){
			throw e;
		}
	}
	
	@Override
	public long size(){
		return screen.size();
	}

}
