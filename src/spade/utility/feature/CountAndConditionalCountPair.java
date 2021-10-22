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

import spade.utility.feature.ConditionalCount.Condition;

public class CountAndConditionalCountPair{

	private final Count countFeature;
	private final ConditionalCount conditionalCountFeature;

	public CountAndConditionalCountPair(final double countFeatureInitialValue,
			final double conditionalCountFeatureInitialValue, final Condition condition){
		this.countFeature = new Count(countFeatureInitialValue);
		this.conditionalCountFeature = new ConditionalCount(conditionalCountFeatureInitialValue, condition);
	}

	public void update(){
		this.update(1.0);
	}

	public void update(final double count){
		this.countFeature.update(count);
		this.conditionalCountFeature.update(count);
	}

	public double getCount(){
		return this.countFeature.get();
	}

	public double getConditionalCount(){
		return this.conditionalCountFeature.get();
	}
}
