/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Dawood Tariq and Raza Ahmad
 */
public class Settings
{

    private static final String settingsFile = "cfg/settings.config";
    private final static Properties spadeProperties = new Properties();

    static
    {
        // load general settings
        setProperty("spade_root", "./");
        setProperty("local_control_port", "19999");
        setProperty("commandline_query_port", "19998");
        setProperty("remote_sketch_port", "29998");
        setProperty("connection_timeout", "15000");
        setProperty("source_reporter", "source_reporter");
        setProperty("direction_ancestors", "ancestors");
        setProperty("direction_descendants", "descendants");
        setProperty("logger_level", Level.ALL.getName());
        setProperty("direction_both", "both");
        setProperty("storage_identifier", "storageID");
        setProperty("default_query_storage", "Neo4j");
        setProperty("neo4j_webserver", "true");

        // override certain settings if the settings file is present
        try
        {
            File f = new File(settingsFile);
            if(f.exists() && !f.isDirectory())
            {
                spadeProperties.load(new FileInputStream(settingsFile));
            }
            else
            {
                Logger.getLogger(Settings.class.getName()).log(Level.INFO, "Default settings maintained", (Throwable) null);
            }
        }
        catch(IOException ex)
        {
            Logger.getLogger(Settings.class.getName()).log(Level.SEVERE, "Error Reading Settings File", ex);
        }
    }

    public static String getProperty(String property) {
        return spadeProperties.getProperty(property);
    }

    public static void setProperty(String property, String value) {
        spadeProperties.setProperty(property.toLowerCase(), value);
    }

    public static void saveSettings() throws IOException {
        spadeProperties.store(new FileOutputStream(settingsFile), null);
    }

    public static String getDefaultConfigFilePath(Class<?> forClass){
        return "cfg/" + forClass.getName() + ".config";
    }
    
    public static String getDefaultOutputFilePath(Class<?> forClass){
    	return "cfg/" + forClass.getName() + ".out";
    }
}
