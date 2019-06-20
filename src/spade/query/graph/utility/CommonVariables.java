package spade.query.graph.utility;

public class CommonVariables
{
    public static final String EDGE_TABLE = "edge";
    public static final String VERTEX_TABLE = "vertex";
    public static final String PRIMARY_KEY = "hash";
    public static final String CHILD_VERTEX_KEY = "childVertexHash";
    public static final String PARENT_VERTEX_KEY = "parentVertexHash";

    public enum Direction
    {
        kAncestor,
        kDescendant,
        kBoth
    }
}
