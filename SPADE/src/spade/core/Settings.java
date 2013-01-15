/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.core;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * @author dawood
 */
public class Settings {

    private static final String settingsFile = "../../cfg/settings.config";
    private final static Properties prop = new Properties();

    static {
        try {
            prop.load(new FileInputStream(settingsFile));
        } catch (IOException ex) {
            setProperty("local_control_port", "19999");
            setProperty("local_query_port", "19998");
            setProperty("remote_query_port", "29999");
            setProperty("remote_sketch_port", "29998");
            setProperty("connection_timeout", "15000");
            setProperty("spade_root", "../../");
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
