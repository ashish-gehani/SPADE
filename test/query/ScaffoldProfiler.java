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
package query;

import com.mysql.jdbc.StringUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.query.scaffold.Scaffold;
import spade.storage.PostgreSQL;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static spade.core.AbstractStorage.DIRECTION;
import static spade.core.AbstractStorage.MAX_DEPTH;
import static spade.core.AbstractStorage.PRIMARY_KEY;


public class ScaffoldProfiler
{
    private static PostgreSQL sqlStorage = new PostgreSQL();
    private static Scaffold scaffold;

    public static void populateScaffold()
    {
        String query = "SELECT hash, childVertexHash, parentVertexHash FROM edge;";
        try
        {
            ResultSet result = sqlStorage.executeQuery(query);
            AbstractEdge incomingEdge;

            while (result.next())
            {
                String edgeHash = result.getString(1);
                String childVertexHash = result.getString(2);
                String parentVertexHash = result.getString(3);
                AbstractVertex childVertex = new Vertex();
                childVertex.addAnnotation("hash", childVertexHash);
                AbstractVertex parentVertex = new Vertex();
                parentVertex.addAnnotation("hash", parentVertexHash);
                incomingEdge = new Edge(childVertex, parentVertex);
                incomingEdge.addAnnotation("hash", edgeHash);
                System.out.println(incomingEdge);
                System.exit(0);
                if(!(StringUtils.isNullOrEmpty(childVertexHash) || StringUtils.isNullOrEmpty(parentVertexHash)))
                {
                    scaffold.insertEntry(incomingEdge);
                }
            }
        }
        catch (SQLException ex)
        {
            System.out.println("Edge set querying unsuccessful!");
        }

    }

    public static void main(String[] args)
    {
        String connectionString = "org.postgresql.Driver jdbc:postgresql://localhost/spade_pg raza spade";
        if(sqlStorage.initialize(connectionString))
        {
            AbstractQuery.setCurrentStorage(sqlStorage);
            scaffold = AbstractStorage.scaffold;
//            sqlStorage.setCursorFetchSize(1000);
//            scaffold.readData(5);
//            System.exit(0);
//            populateScaffold();
        }
        else
            System.exit(-1);

        String direction = "d";
        String maxDepth = "5";
        int maxIterations = 1000;
        String hashFile = "/Users/raza/hashes.scaffold";
        String file_name = "scaffold_with_indices_latest_proper_indexes.stats_maxDepth=" + maxDepth;

        FileWriter fw;
        BufferedWriter bw;
        PrintWriter pw = null;
        try
        {
            Scanner s = new Scanner(new File(hashFile));
            ArrayList<String> hashList = new ArrayList<>();
            while (s.hasNext())
            {
                hashList.add(s.next());
            }
            s.close();
            fw = new FileWriter(file_name, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);
            for(int iteration = 0; iteration < maxIterations; iteration++)
            {
                String rootHash = hashList.get(iteration);
                String string = "Iteration:" + (iteration + 1) + "\n";
                Map<String, List<String>> params = new LinkedHashMap<>();
                params.put(PRIMARY_KEY, Arrays.asList(AbstractQuery.OPERATORS.EQUALS, rootHash, null));
                params.put(DIRECTION, Arrays.asList(direction));
                params.put(MAX_DEPTH, Arrays.asList(maxDepth));
                long start_time = System.nanoTime();
                Graph result = scaffold.queryManager(params);
                long elapsed_time = System.nanoTime() - start_time;
                int vertices = result != null ? result.vertexSet().size() : 0;
                int edges = result != null ? result.edgeSet().size() : 0;
                string += "Hash:" + rootHash + "\n";
                string += "Vertices: " + vertices + "\n";
                string += "Edges: " + edges + "\n";
                string += "Time Duration: " + elapsed_time / 1000000000.0 + " s. \n";
                string += "---------------";
                pw.println(string);
                pw.flush();
            }
        }
        catch(IOException e)
        {
            System.err.println("Error opening file");
            System.exit(-1);
        }
        finally
        {
            if(pw != null)
                pw.close();
        }
    }
}
