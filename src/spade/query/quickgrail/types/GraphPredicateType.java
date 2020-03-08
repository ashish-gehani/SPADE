/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.query.quickgrail.types;

import spade.query.quickgrail.entities.GraphPredicate;

public class GraphPredicateType extends Type{
	static private GraphPredicateType instance;

	public static GraphPredicateType GetInstance(){
		if(instance == null){
			instance = new GraphPredicateType();
		}
		return instance;
	}

	@Override
	public TypeID getTypeID(){
		return TypeID.kGraphPredicate;
	}

	@Override
	public String getName(){
		return "GraphPredicate";
	}

	@Override
	public Object parseValueFromString(String text){
		throw new RuntimeException("Not supported");
	}

	@Override
	public String printValueToString(Object value){
		if(!(value instanceof GraphPredicate)){
			if(value == null){
				throw new RuntimeException("NULL type. Expected '"+GraphPredicate.class+"'");
			}else{
				throw new RuntimeException("Unexpected type '"+value.getClass()+"'. Expected '"+GraphPredicate.class+"'");
			}
		}else{
			return ((GraphPredicate)value).predicateRoot.toString();
		}
	}
}
