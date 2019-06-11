/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.storage.quickstep;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.storage.Quickstep;

/**
 * Helper class for loading Quickstep configuration parameters.
 */
public class QuickstepConfiguration {
  // Default bulk insert batch size
  private static final int DEFAULT_EDGE_BATCH_SIZE = 1024 * 256;
  private static final int MINIMUM_EDGE_BATCH_SIZE = 1024 * 16;

  // Measured in seconds.
  private static final int DEFAULT_FORCE_SUBMIT_TIME_INTERVAL = 180;
  private static final int MINIMUM_FORCE_SUBMIT_TIME_INTERVAL = 30;

  // Default Quickstep server address.
  private static final String DEFAULT_QUICKSTEP_SERVER_IP = "0.0.0.0";
  private static final int DEFAULT_QUICKSTEP_SERVER_PORT = 3000;

  // Default maximum field length.
  private static final int DEFAULT_VERTEX_ANNOTATION_MAX_KEY_LENGTH = 32;
  private static final int DEFAULT_VERTEX_ANNOTATION_MAX_VALUE_LENGTH = 65536;
  private static final int DEFAULT_EDGE_ANNOTATION_MAX_KEY_LENGTH = 32;
  private static final int DEFAULT_EDGE_ANNOTATION_MAX_VALUE_LENGTH = 256;

  private static final int MINIMUM_VERTEX_ANNOTATION_MAX_KEY_LENGTH = 32;
  private static final int MINIMUM_VERTEX_ANNOTATION_MAX_VALUE_LENGTH = 65536;
  private static final int MINIMUM_EDGE_ANNOTATION_MAX_KEY_LENGTH = 32;
  private static final int MINIMUM_EDGE_ANNOTATION_MAX_VALUE_LENGTH = 256;

  // Default key-value cache parameters.
  private static final String DEFAULT_CACHE_DATABASE_PATH = "tmp";
  private static final String DEFAULT_CACHE_DATABASE_NAME = "qsCacheDB";
  private static final int DEFAULT_CACHE_SIZE = 1000000;
  private static final double DEFAULT_CACHE_BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY = 0.0001;
  private static final int DEFAULT_CACHE_BOOLM_FILTER_EXPECTED_NUMBER_OF_ELEMENTS = 1000000;

  // Properties loaded from configuration file.
  private Properties qsProperties = new Properties();

  // Actual configurations.
  private String serverIP;
  private int serverPort;

  private String debugLogFilePath;
  private int edgeBatchSize;
  private int forceSubmitTimeInterval;

  private int maxVertexKeyLength;
  private int maxVertexValueLength;
  private int maxEdgeKeyLength;
  private int maxEdgeValueLength;

  private String cacheDatabasePath;
  private String cacheDatabaseName;
  private int cacheSize;
  private double cacheBloomfilterFalsePositiveProbability;
  private int cacheBloomFilterExpectedNumberOfElements;

  private String reset;

  private Logger logger = Logger.getLogger(Quickstep.class.getName());

  public QuickstepConfiguration(String configFilePath, String arguments) {
    try {
      qsProperties.load(new FileInputStream(configFilePath));
    } catch (IOException e) {
      String msg = "Failed loading Quickstep configuration file -- will use default configuration";
      logger.log(Level.WARNING, msg, e);
    }

    // Merge properties from command line arguments.
    mergeProperties(arguments);

    serverIP = getPropertyOrDefault("serverIP", DEFAULT_QUICKSTEP_SERVER_IP);
    serverPort = getPropertyOrDefault("serverPort", DEFAULT_QUICKSTEP_SERVER_PORT);

    debugLogFilePath = getProperty("debugLogFilePath");

    // Batch size.
    edgeBatchSize = getPropertyOrDefault("batchSize",
                                         DEFAULT_EDGE_BATCH_SIZE,
                                         MINIMUM_EDGE_BATCH_SIZE);

    // Force-submit time interval.
    forceSubmitTimeInterval = getPropertyOrDefault("batchTimeIntervalInSeconds",
                                                   DEFAULT_FORCE_SUBMIT_TIME_INTERVAL,
                                                   MINIMUM_FORCE_SUBMIT_TIME_INTERVAL);

    // Graph annotation fields max length.
    maxVertexKeyLength = getPropertyOrDefault("maxVertexKeyLength",
                                              DEFAULT_VERTEX_ANNOTATION_MAX_KEY_LENGTH,
                                              MINIMUM_VERTEX_ANNOTATION_MAX_KEY_LENGTH);
    maxVertexValueLength = getPropertyOrDefault("maxVertexValueLength",
                                                DEFAULT_VERTEX_ANNOTATION_MAX_VALUE_LENGTH,
                                                MINIMUM_VERTEX_ANNOTATION_MAX_VALUE_LENGTH);
    maxEdgeKeyLength = getPropertyOrDefault("maxEdgeKeyLength",
                                            DEFAULT_EDGE_ANNOTATION_MAX_KEY_LENGTH,
                                            MINIMUM_EDGE_ANNOTATION_MAX_KEY_LENGTH);
    maxEdgeValueLength = getPropertyOrDefault("maxEdgeValueLength",
                                              DEFAULT_EDGE_ANNOTATION_MAX_VALUE_LENGTH,
                                              MINIMUM_EDGE_ANNOTATION_MAX_VALUE_LENGTH);

    // Key-value cache configuration.
    cacheDatabasePath = getPropertyOrDefault("cacheDatabasePath", DEFAULT_CACHE_DATABASE_PATH);
    cacheDatabaseName = getPropertyOrDefault("cacheDatabaseName", DEFAULT_CACHE_DATABASE_NAME);
    cacheSize = getPropertyOrDefault("cacheSize", DEFAULT_CACHE_SIZE);

    cacheBloomfilterFalsePositiveProbability =
        getPropertyOrDefault("cacheBloomfilterFalsePositiveProbability",
                             DEFAULT_CACHE_BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY);

    cacheBloomFilterExpectedNumberOfElements =
        getPropertyOrDefault("cacheBloomFilterExpectedNumberOfElements",
                             DEFAULT_CACHE_BOOLM_FILTER_EXPECTED_NUMBER_OF_ELEMENTS);

    // Whether to reset database.
    reset = getProperty("reset");
  }

  /**
   * @return Quickstep Server IP address.
   */
  public String getServerIP() {
    return serverIP;
  }

  /**
   * @return Quickstep Server IP port.
   */
  public int getServerPort() {
    return serverPort;
  }

  /**
   * @return Prioritized log file location.
   */
  public String getDebugLogFilePath() {
    return debugLogFilePath;
  }

  /**
   * @return Batch size for bulk loading graph data into Quickstep.
   */
  public int getBatchSize() {
    return edgeBatchSize;
  }

  /**
   * @return Time interval for forced flushing of incoming graph data into Quickstep.
   */
  public int getForceSubmitTimeInterval() {
    return forceSubmitTimeInterval;
  }

  /**
   * @return Maximum vertex annotation key length to be stored.
   */
  public int getMaxVertexKeyLength() {
    return maxVertexKeyLength;
  }

  /**
   * @return Maximum vertex annotation value length to be stored.
   */
  public int getMaxVertexValueLength() {
    return maxVertexValueLength;
  }

  /**
   * @return Maximum edge annotation key length to be stored.
   */
  public int getMaxEdgeKeyLength() {
    return maxEdgeKeyLength;
  }

  /**
   * @return Maximum edge annotation value length to be stored.
   */
  public int getMaxEdgeValueLength() {
    return maxEdgeValueLength;
  }

  /**
   * @return The external database (directory) path for the key-value cache.
   */
  public String getCacheDatabasePath() {
    return cacheDatabasePath;
  }

  /**
   * @return The external database name for the key-value cache.
   */
  public String getCacheDatabaseName() {
    return cacheDatabaseName;
  }

  /**
   * @return The capacity of the key-value cache.
   */
  public int getCacheSize() {
    return cacheSize;
  }

  /**
   * @return The false positive rate of the bloom filter used by the key-value cache.
   */
  public double getCacheBloomfilterFalsePositiveProbability() {
    return cacheBloomfilterFalsePositiveProbability;
  }

  /**
   * @return The cardinality of the bloom filter used by the key-value cache.
   */
  public int getCacheBloomFilterExpectedNumberOfElements() {
    return cacheBloomFilterExpectedNumberOfElements;
  }

  /**
   * @return Whether to reset Quickstep database on initialization.
   */
  public boolean getReset() {
    return reset != null && reset.equalsIgnoreCase("true");
  }

  /**
   * Merge command line arguments into the properties (initially loaded from
   * configuration file spade.storage.Quickstep.config).
   */
  private void mergeProperties(String arguments) {
    if (arguments == null || arguments.trim().isEmpty()) {
      return;
    }
    Pattern keyValuePairPattern = Pattern.compile("[a-zA-Z]*\\s*=\\s*(\\\\ |[^ ])*");
    Matcher m = keyValuePairPattern.matcher(arguments);
    while (m.find()) {
      String[] pair = m.group().split("=", 2);
      if (pair.length == 2) {
        String key = pair[0].trim();
        String value = (pair[1] + ".").trim().replace("\\ ", " ");
        value = value.substring(0, value.length()-1);
        qsProperties.put(key, value);
      }
    }
  }

  private int getPropertyOrDefault(String key, int defaultValue) {
    String value = qsProperties.getProperty(key);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (Exception e) {
        String msg = "Invalid value " + value + " for property \"" + key + "\", " +
                     "will use default value " + defaultValue + ".";
        logger.log(Level.SEVERE, msg, e);
      }
    }
    return defaultValue;
  }

  private double getPropertyOrDefault(String key, double defaultValue) {
    String value = qsProperties.getProperty(key);
    if (value != null) {
      try {
        return Double.parseDouble(value);
      } catch (Exception e) {
        String msg = "Invalid value " + value + " for property \"" + key + "\", " +
                     "will use default value " + defaultValue + ".";
        logger.log(Level.SEVERE, msg, e);
      }
    }
    return defaultValue;
  }

  private int getPropertyOrDefault(String key, int defaultValue, int minimumValue) {
    int value = getPropertyOrDefault(key, defaultValue);
    if (value < minimumValue) {
      String msg = "The value " + value + " for property \"" + key + "\" " +
                   "is to low -- adjusted to " + minimumValue;
      logger.log(Level.WARNING, msg);
      value = minimumValue;
    }
    return value;
  }

  private String getPropertyOrDefault(String key, String defaultValue) {
    return qsProperties.getProperty(key, defaultValue);
  }

  private String getProperty(String key) {
    return qsProperties.getProperty(key);
  }

  /**
   * @return Configuration information.
   */
  public String dump() {
    String info = "List of configurations:\n" +
                  "-----------------------\n" +
                  "serverIP = " + serverIP + "\n" +
                  "serverPort = " + serverPort + "\n" +
                  "batchSize = " + edgeBatchSize + "\n" +
                  "batchTimeIntervalInSeconds = " + forceSubmitTimeInterval + "\n" +
                  "maxVertexKeyLength = " + maxVertexKeyLength + "\n" +
                  "maxVertexValueLength = " + maxVertexValueLength + "\n" +
                  "maxEdgeKeyLength = " + maxEdgeKeyLength + "\n" +
                  "maxEdgeValueLength = " + maxEdgeValueLength + "\n" +
                  "cacheDatabasePath = " + cacheDatabasePath + "\n" +
                  "cacheDatabaseName = " + cacheDatabaseName + "\n" +
                  "cacheSize = " + cacheSize + "\n" +
                  "cacheBloomfilterFalsePositiveProbability = " + cacheBloomfilterFalsePositiveProbability + "\n" +
                  "cacheBloomFilterExpectedNumberOfElements = " + cacheBloomFilterExpectedNumberOfElements + "\n";

    if (debugLogFilePath != null) {
      info += "debugLogFilePath = " + debugLogFilePath + "\n";
    }

    if (reset != null) {
      info += "reset = " + reset + "\n";
    }

    info += "-----------------------";
    return info;
  }
}
