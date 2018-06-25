package spade.query.scaffold;


import java.util.logging.Level;
import java.util.logging.Logger;

public class ScaffoldFactory
{

    public static Scaffold createScaffold(String scaffoldDatabaseName)
    {
        Scaffold scaffold;
        String packagePath = "spade.query.scaffold.";
        try
        {
            scaffold = (Scaffold) Class.forName(packagePath + scaffoldDatabaseName).newInstance();
        }
        catch(Exception ex)
        {
            scaffold = createDefaultScaffold();
            Logger.getLogger(ScaffoldFactory.class.getName()).log(Level.SEVERE, "Scaffold database not found! Creating default Scaffold", ex);
        }

        return scaffold;
    }

    public static Scaffold createDefaultScaffold()
    {
        return new BerkeleyDB();
    }
}
