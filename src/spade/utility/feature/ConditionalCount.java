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
package spade.utility.feature;

public class ConditionalCount{

	private final Count countFeature;
	private final Condition condition;
	
	public ConditionalCount(final double initialValue, final Condition condition){
		this.countFeature = new Count(initialValue);
		this.condition = condition;
	}

	public void update(){
		this.update(1.0);
	}

	public void update(final double count){
		if(this.condition.isSatisfied()){
			this.countFeature.update(count);
		}
	}

	public double get(){
		return this.countFeature.get();
	}
	
	public static interface Condition{
		public boolean isSatisfied();
	}
}
