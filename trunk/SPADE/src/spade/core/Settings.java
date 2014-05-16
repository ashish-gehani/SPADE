/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2014 SRI International

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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * @author Dawood Tariq
 */
public class Settings {

    private static final String settingsFile = "conf/settings.config";
    private final static Properties prop = new Properties();

    static {
        try {
            prop.load(new FileInputStream(settingsFile));
        } catch (IOException ex) {
            setProperty("spade_root", "../");
            setProperty("local_control_port", "19999");
            setProperty("local_query_port", "19998");
            setProperty("remote_query_port", "29999");
            setProperty("remote_sketch_port", "29998");
            setProperty("connection_timeout", "15000");
            setProperty("source_reporter", "source_reporter");
            setProperty("direction_ancestors", "ancestors");
            setProperty("direction_descendants", "descendants");
            setProperty("direction_both", "both");
            setProperty("storage_identifier", "storageID");
            setProperty("default_query_storage", "Neo4j");
            setProperty("neo4j_webserver", "true");
        }
    }

    public static String getProperty(String property) {
        return prop.getProperty(property);
    }

    public static void setProperty(String property, String value) {
        prop.setProperty(property.toLowerCase(), value);
    }

    public static void saveSettings() throws IOException {
        prop.store(new FileOutputStream(settingsFile), null);
    }
}
