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
package spade.query.quickgrail.core;

import java.util.ArrayList;

import spade.query.quickgrail.parser.ParseExpression;
import spade.query.quickgrail.parser.ParseExpression.ExpressionType;
import spade.query.quickgrail.parser.ParseName;

public class ExpressionStream{
	private ArrayList<ParseExpression> stream;
	private int position = 0;

	public ExpressionStream(ArrayList<ParseExpression> stream){
		this.stream = stream;
		this.position = 0;
	}

	public boolean hasNext(){
		return position < stream.size();
	}

	public ParseExpression getNextExpression(){
		if(!hasNext()){
			throw new RuntimeException("Require more arguments");
		}
		return stream.get(position++);
	}

	public String tryGetNextNameAsString(){
		if(!hasNext()){
			return null;
		}
		ParseExpression expression = stream.get(position);
		if(expression.getExpressionType() != ExpressionType.kName){
			return null;
		}
		++position;
		return ((ParseName)expression).getName().getValue();
	}

	public String getNextString(){
		if(!hasNext()){
			throw new RuntimeException("Require more arguments");
		}
		return QueryResolverHelper.resolveString(stream.get(position++));
	}

	public String getNextNameOrString(){
		if(!hasNext()){
			throw new RuntimeException("Require more arguments");
		}
		ParseExpression expression = stream.get(position++);
		switch(expression.getExpressionType()){
		case kName:
			return QueryResolverHelper.resolveNameAsString(expression);
		case kLiteral:
			return QueryResolverHelper.resolveString(expression);
		default:
			break;
		}
		throw new RuntimeException("Expected name or string literal");
	}

}