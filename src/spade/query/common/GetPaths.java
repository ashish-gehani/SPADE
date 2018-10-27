/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.query.common;

import spade.core.AbstractQuery;
import spade.core.Graph;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
import static spade.core.AbstractStorage.DIRECTION_DESCENDANTS;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class GetPaths extends AbstractQuery<Graph>
{
    public GetPaths()
    {
        register();
    }

    @Override
    public Graph execute(String argument_string)
    {
        Pattern argument_pattern = Pattern.compile(",");
        String[] arguments = argument_pattern.split(argument_string);
        String constraints = arguments[0].trim();
        int maxLength = Integer.parseInt(arguments[1].trim());
        String direction = arguments[2].trim();
        Map<String, List<String>> parameters = parseConstraints(constraints);
        // assumption that 'sourceVertexHash' and 'destinationVertexHash' keys are present
        GetLineage getLineage = new GetLineage();
        parameters.put(PRIMARY_KEY, parameters.get("sourceVertexHash"));
        parameters.put("direction", Collections.singletonList(DIRECTION_ANCESTORS));
        Graph ancestorLineage = getLineage.execute(parameters, direction, maxLength);
        parameters.put("direction", Collections.singletonList(DIRECTION_DESCENDANTS));
        parameters.put(PRIMARY_KEY, parameters.get("destinationVertexHash"));
        Graph descendantLineage = getLineage.execute(parameters, direction, maxLength);

        return Graph.union(ancestorLineage, descendantLineage);
    }

    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        return null;
    }
}
