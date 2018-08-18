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
