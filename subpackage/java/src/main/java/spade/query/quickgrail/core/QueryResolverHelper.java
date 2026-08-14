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
import spade.query.quickgrail.parser.ParseLiteral;
import spade.query.quickgrail.parser.ParseName;
import spade.query.quickgrail.types.TypeID;
import spade.query.quickgrail.types.TypedValue;

public class QueryResolverHelper{

	public static Integer resolveInteger(ParseExpression expression){
		if(expression.getExpressionType() != ParseExpression.ExpressionType.kLiteral){
			throw new RuntimeException(
					"Invalid value at " + expression.getLocationString() + ": expected integer literal");
		}
		TypedValue value = ((ParseLiteral)expression).getLiteralValue();
		if(value.getType().getTypeID() != TypeID.kInteger){
			throw new RuntimeException(
					"Invalid value type at " + expression.getLocationString() + ": expected integer");
		}
		return (Integer)value.getValue();
	}

	public static String resolveString(ParseExpression expression){
		if(expression.getExpressionType() != ParseExpression.ExpressionType.kLiteral){
			throw new RuntimeException(
					"Invalid value at " + expression.getLocationString() + ": expected string literal");
		}
		TypedValue value = ((ParseLiteral)expression).getLiteralValue();
		if(value.getType().getTypeID() != TypeID.kString){
			throw new RuntimeException("Invalid value type at " + expression.getLocationString() + ": expected string");
		}
		return (String)value.getValue();
	}

	public static String resolveNameAsString(ParseExpression expression){
		if(expression.getExpressionType() != ParseExpression.ExpressionType.kName){
			throw new RuntimeException("Invalid value at " + expression.getLocationString() + ": expected name");
		}
		return ((ParseName)expression).getName().getValue();
	}

	public static final Integer getOptionalPositiveIntegerArgument(final ArrayList<ParseExpression> arguments, final int i){
		if(i >= arguments.size()){
			return null;
		}else{
			if(i < 0){
				throw new RuntimeException("Negative index for argument");
			}
			final Integer value = resolveInteger(arguments.get(i));
			if(value < 1){
				throw new RuntimeException("Only positive value for limit allowed");
			}
			return value;
		}
	}

	public static boolean isNameOrLiteral(final ParseExpression argument){
		final ParseExpression expr = argument;
		if(expr.getExpressionType() == ParseExpression.ExpressionType.kName){
			return true;
		}else if(expr.getExpressionType() == ParseExpression.ExpressionType.kLiteral){
			final TypedValue typedValue = ((ParseLiteral)expr).getLiteralValue();
			if(typedValue.getType().getTypeID() == TypeID.kString){
				return true;
			}else if(typedValue.getType().getTypeID() == TypeID.kInteger){
				return true;
			}
		}
		return false;
	}

	public static boolean isIntegerLiteral(final ParseExpression argument){
		final ParseExpression expr = argument;
		if(expr.getExpressionType() == ParseExpression.ExpressionType.kLiteral){
			final TypedValue typedValue = ((ParseLiteral)expr).getLiteralValue();
			if(typedValue.getType().getTypeID() == TypeID.kInteger){
				return true;
			}
		}
		return false;
	}

	public static String resolveNameOrLiteralAsString(final ParseExpression argument){
		final ParseExpression expr = argument;
		if(expr.getExpressionType() == ParseExpression.ExpressionType.kName){
			final String value = resolveNameAsString(expr);
			return value;
		}else if(expr.getExpressionType() == ParseExpression.ExpressionType.kLiteral){
			final TypedValue typedValue = ((ParseLiteral)expr).getLiteralValue();
			final String value;
			if(typedValue.getType().getTypeID() == TypeID.kString){
				value = (String)typedValue.getValue();
			}else if(typedValue.getType().getTypeID() == TypeID.kInteger){
				value = ((Integer)typedValue.getValue()).toString();
			}else{
				throw new RuntimeException("Invalid value type at " + expr.getLocationString() + ": expected name or string or integer");
			}
			return value;
		}else{
			throw new RuntimeException("Invalid value type at " + expr.getLocationString() + ": name or expected string or integer");
		}
	}

}
