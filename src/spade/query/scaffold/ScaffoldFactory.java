package spade.query.scaffold;


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
        else if(scaffoldType.equals("InMemory"))
        {
            scaffold = new InMemory();
        }
        else if(scaffoldType.equals("Redis"))
        {
            scaffold = new Redis();
        }
        else if(scaffoldType.equals("PostgreSQL"))
        {
            scaffold = new PostgreSQL();
        }

        return scaffold;
    }

    public static Scaffold createDefaultScaffold()
    {
        return new PostgreSQL();
    }
}
