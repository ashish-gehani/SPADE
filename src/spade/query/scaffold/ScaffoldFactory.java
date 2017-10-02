package spade.query.scaffold;


import java.util.Objects;

public class ScaffoldFactory
{
    private ScaffoldFactory(){}

    public static Scaffold createScaffold(String scaffoldType)
    {
        Scaffold scaffold = null;
        if(scaffoldType.equals("BerkeleyDB"))
        {
            scaffold = new BerkeleyDB();
        }
        else if(scaffoldType.equals("LevelDB"))
        {
            scaffold = new LevelDB();
        }

        return scaffold;
    }

    public static Scaffold createDefaultScaffold()
    {
        return new BerkeleyDB();
    }
}
