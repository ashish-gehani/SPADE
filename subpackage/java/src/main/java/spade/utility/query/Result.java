/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International
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

package spade.utility.query;

import spade.core.Graph;
import spade.core.Query;
import spade.query.quickgrail.instruction.SaveGraph;
import spade.query.quickgrail.utility.ResultTable;

public class Result {

    private Result() {
    }

    public static Query ensureQuery(final Object resultObject) throws Exception {
        if (!(resultObject instanceof Query)) {
            throw new Exception(
                "Server sent back unknown response. Expected: "
                + Query.class.getName()
                + ". Actual: "
                + (resultObject == null ? "null" : resultObject.getClass().getName())
            );
        }
        return (Query) resultObject;
    }

    public static String format(final Query response, final SaveGraph.Format graphFormat) throws Exception {
        if (!response.wasQuerySuccessful()) {
            return errorToString(response.getError());
        }
        return resultToString(response.getResult(), graphFormat);
    }

    public static String errorToString(final Object errorObject) {
        if (errorObject == null) {
            return "";
        } else if (errorObject instanceof Throwable) {
            return "Error: " + ((Throwable) errorObject).getMessage();
        } else {
            return "Error: " + String.valueOf(errorObject);
        }
    }

    public static String resultToString(final Object responseResult, final SaveGraph.Format graphFormat) throws Exception {
        if (responseResult == null) {
            return "";
        }

        if (responseResult instanceof Graph) {
            return Graph.exportGraphToString(graphFormat, (Graph) responseResult);
        } else if (responseResult instanceof ResultTable) {
            return ((ResultTable) responseResult).toString();
        } else {
            return String.valueOf(responseResult);
        }
    }

}
