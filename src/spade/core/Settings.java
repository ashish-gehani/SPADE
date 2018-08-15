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

    private static final String settingsFile = "cfg/spade.core.Kernel.config";
    private final static Properties spadeProperties = new Properties();

    static
    {
        // load settings from the file
        try
        {
            spadeProperties.load(new FileInputStream(settingsFile));
        }
        catch(Exception ex)
        {
            Logger.getLogger(Settings.class.getName()).log(Level.SEVERE, "Error reading settings file! Shutting down...", ex);
            System.exit(-1);
        }
    }

    public static String getProperty(String property) {
        return spadeProperties.getProperty(property);
    }

    public static String getDefaultConfigFilePath(Class<?> forClass){
        return "cfg/" + forClass.getName() + ".config";
    }
    
    public static String getDefaultOutputFilePath(Class<?> forClass){
    	return "cfg/" + forClass.getName() + ".out";
    }
}
