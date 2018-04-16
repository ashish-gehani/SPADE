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

import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Series<T extends Serializable & Comparable<T>, V extends Serializable> implements Serializable{

	private static final long serialVersionUID = -5587048659400431438L;

	private static final Logger logger = Logger.getLogger(Series.class.getName());
	
	private List<SimpleEntry<T, V>> series = new ArrayList<SimpleEntry<T, V>>();
	
	private boolean sorted = false;
	
	public void add(T t, V v){
		if(t == null){
			logger.log(Level.WARNING, "NOT ADDED. Key cannot be null. Value = " + v);
		}else{
			sorted = false;
			SimpleEntry<T, V> entry = new SimpleEntry<T, V>(t, v);
			series.add(entry);
		}
	}
	
	// Caller must make sure about not null
	public V getBestMatch(T t){
		if(t == null){
			logger.log(Level.WARNING, "Key to look up cannot be null.");
		}else{
			if(!sorted){
				sorted = true;
				Collections.sort(series, new Comparator<SimpleEntry<T,V>>(){
					@Override
					public int compare(SimpleEntry<T, V> o1, SimpleEntry<T, V> o2){
						if(o1 == null && o2 == null){
							return 0;
						}else if(o1 == null && o2 != null){
							return -1;
						}else if(o1 != null && o2 == null){
							return 1;
						}else{
							T t1 = o1.getKey();
							T t2 = o2.getKey();
							if(t1 == null && t2 == null){
								return 0;
							}else if(t1 == null && t2 != null){
								return -1;
							}else if(t1 != null && t2 == null){
								return 1;
							}else{
								return t1.compareTo(t2);
							}
						}
					}
				});
			}
			// Looking up in reverse because we want the last associated value for that key.
			for(int a = series.size() - 1; a > -1; a--){
				SimpleEntry<T, V> entry = series.get(a);
				T time = entry.getKey();
				if(t.compareTo(time) >= 0){
					return entry.getValue();
				}
			}
		}
		return null; // none matched
	}
	
}