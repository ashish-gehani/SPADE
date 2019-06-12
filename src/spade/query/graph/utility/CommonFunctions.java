package spade.query.graph.utility;

import org.apache.commons.lang3.math.NumberUtils;
import spade.core.AbstractVertex;
import spade.core.Vertex;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static spade.core.AbstractQuery.currentStorage;
import static spade.query.graph.utility.CommonVariables.PRIMARY_KEY;

public class CommonFunctions
{
    private static Logger logger = Logger.getLogger(CommonFunctions.class.getName());

    public static boolean compareValues(String subject_value_str, String value_str, String operation)
    {
        boolean comparison = false;
        if(subject_value_str != null)
        {
            boolean isNumeric = NumberUtils.isParsable(value_str);
            if(isNumeric)
            {
                Double value_to_compare = NumberUtils.createDouble(value_str);
                Double subject_value = NumberUtils.createDouble(subject_value_str);
                switch(operation)
                {
                    case "=":
                        if(subject_value_str.equals(value_str))
                            comparison = true;
                        break;
                    case "<>":
                        if(!subject_value_str.equals(value_str))
                            comparison = true;
                        break;
                    //TODO: implement robust double comparison using threshold
                    case "<":
                        if(subject_value < value_to_compare)
                            comparison = true;
                        break;
                    case "<=":
                        if(subject_value <= value_to_compare)
                            comparison = true;
                        break;
                    case ">":
                        if(subject_value > value_to_compare)
                            comparison = true;
                        break;
                    case ">=":
                        if(subject_value >= value_to_compare)
                            comparison = true;
                        break;
                    default:

                }
            }
            else
            {
                switch(operation)
                {
                    case "LIKE":
                        // escape all 12 special characters: [](){}.*+?$^|#\
                        value_str = value_str.replaceAll(
                                "(\\[|\\]|\\(|\\)|\\{|\\}|\\.|\\*|\\+|\\?|\\$|\\^|\\||\\#|\\\\)",
                                "\\\\$1");
                        // Reassuring note: this dangerous-looking regular expression has been well tested.

                        // convert SQL's LIKE syntax into java regular expression syntax
                        value_str = value_str.replace("_", ".").replace("%", ".*");
                        Pattern pattern = Pattern.compile(value_str);
                        if(pattern.matcher(value_str).matches())
                        {
                            comparison = true;
                        }
                        break;
                    case "REGEXP":
                        // coming up soon.
                        // TODO: requires conversion between POSIX regex and JAVA regex
                        break;
                }
            }
        }
        return comparison;
    }

    public static void executeGetVertex(Set<AbstractVertex> vertexSet, String sqlQuery)
    {
        logger.log(Level.INFO, "Executing query: " + sqlQuery);
        ResultSet result = (ResultSet) currentStorage.executeQuery(sqlQuery);
        ResultSetMetaData metadata;
        try
        {
            metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for(int i = 1; i <= columnCount; i++)
            {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            while(result.next())
            {
                AbstractVertex vertex = new Vertex();
                for(int i = 1; i <= columnCount; i++)
                {
                    String colName = columnLabels.get(i);
                    String value = result.getString(i);
                    if(value != null)
                    {
                        if(colName != null && !colName.equals(PRIMARY_KEY))
                        {
                            vertex.addAnnotation(colName, value);
                        }
                    }
                }
                vertexSet.add(vertex);
            }
        }
        catch(SQLException ex)
        {
            logger.log(Level.SEVERE, "Error executing GetVertex Query", ex);
        }

    }
}
