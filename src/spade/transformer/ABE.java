/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International
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
package spade.transformer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.ABEGraph;

public class ABE extends AbstractTransformer
{
	private static final String FILE_SEPARATOR = "/";
	
    private KeyGenerator keyGenerator;
    private Ciphers cipher;
    private static final String LOW = "low";
    private static final String MEDIUM = "medium";
    private static final String HIGH = "high";
    private static final String BASE_ALGORITHM = "AES";
    private static final String KEYS_DIRECTORY = "keysDirectory";
    private File KEYS_DIR;
    private static final String EDGE = "Edge";
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final Logger logger = Logger.getLogger(ABE.class.getName());

    private List<String> lowAnnotations = new ArrayList<>();
    private List<String> mediumAnnotations = new ArrayList<>();
    private List<String> highAnnotations = new ArrayList<>();
    private Map<String, List<String>> functionMap = new HashMap<>();

    private class SecretKeys
    {
        SecretKey low;
        SecretKey medium;
        SecretKey high;
    }

    private class Ciphers
    {
        Cipher low;
        Cipher medium;
        Cipher high;
    }

    @Override
    public boolean initialize(String arguments)
    {
        boolean configFileStatus = readConfigFile();
        if (!configFileStatus)
        {
            return false;
        }
        try
        {
            // create key generator
            this.keyGenerator = KeyGenerator.getInstance(BASE_ALGORITHM);
            this.keyGenerator.init(128);
            this.cipher = new Ciphers();
            this.cipher.low = Cipher.getInstance(ALGORITHM);
            this.cipher.medium = Cipher.getInstance(ALGORITHM);
            this.cipher.high = Cipher.getInstance(ALGORITHM);
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Error getting instance of key generator or cipher", ex);
            return false;
        }
        return true;
    }

    private boolean readConfigFile()
    {
        String configFileName = Settings.getDefaultConfigFilePath(this.getClass());
        // read config file here and set encryption level
        try
        {
            String keyDirectoryPath = null;
            String level = null;
            List<String> lines = FileUtils.readLines(new File(configFileName), Charsets.UTF_8);
            for (String line : lines)
            {
                line = line.trim();
                if (!StringUtils.isBlank(line) && !line.startsWith("#"))
                {
                    if (line.equalsIgnoreCase(LOW))
                    {
                        level = LOW;
                    }
                    else if (line.equalsIgnoreCase(MEDIUM))
                    {
                        level = MEDIUM;
                    }
                    else if (line.equalsIgnoreCase(HIGH))
                    {
                        level = HIGH;
                    }
                    else if (line.startsWith(KEYS_DIRECTORY))
                    {
                        String[] split = line.split("=");
                        assert (split.length == 2);
                        keyDirectoryPath = split[1].trim();
                    }
                    else
                    {
                        List<String> annotations = new ArrayList<>();
                        String[] annotationsList = line.split(",");
                        assert (level != null);
                        for (String annotation : annotationsList)
                        {
                            String substr = StringUtils.substringBetween(annotation, "[", "]");
                            if (substr != null)
                            {
                                String[] handles = substr.split(":");
                                assert (handles.length == 2);
                                String encryptFunction = handles[0];
                                String decryptFunction = handles[1];
                                annotation = annotation.substring(0, annotation.indexOf("["));
                                functionMap.put(annotation, Arrays.asList(encryptFunction, decryptFunction));
                                annotations.add(annotation);
                            }
                            else
                            {
                                annotations.add(annotation);
                            }
                        }
                        switch (level)
                        {
                            case LOW:
                                lowAnnotations.addAll(annotations);
                                break;
                            case MEDIUM:
                                mediumAnnotations.addAll(annotations);
                                break;
                            case HIGH:
                                highAnnotations.addAll(annotations);
                                break;
                        }
                    }
                }
            }

            if (keyDirectoryPath == null)
            {
                logger.log(Level.SEVERE, "NULL '" + KEYS_DIRECTORY + "'");
                return false;
            }
            else
            {
                File keyDirFile = new File(keyDirectoryPath);
                try
                {
                    if (keyDirFile.isDirectory())
                    {
                        this.KEYS_DIR = keyDirFile;
                        return true;
                    }
                    else
                    {
                        logger.log(Level.SEVERE, "'" + KEYS_DIRECTORY + "' must be a directory");
                        return false;
                    }
                }
                catch (Exception e)
                {
                    logger.log(Level.SEVERE, "Failed to check if '" + KEYS_DIRECTORY + "' is a directory", e);
                    return false;
                }
            }
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to read config file properly", ex);
            return false;
        }
    }

    private SecretKey generateKey()
    {
        return keyGenerator.generateKey();
    }

    private SecretKeys generateSymmetricKeys()
    {
        SecretKeys secretKeys = new SecretKeys();
        secretKeys.low = generateKey();
        secretKeys.medium = generateKey();
        secretKeys.high = generateKey();
        if (secretKeys.low == null || secretKeys.medium == null || secretKeys.high == null)
        {
            logger.log(Level.SEVERE, "Symmetric keys not generated properly");
            return null;
        }
        return secretKeys;
    }

    private String decryptIpAddress(String key, String encryptedValue, Cipher cipher, String level)
    {
        String[] subnets = encryptedValue.split("\\.");
        String decryptedValue;
        switch (level)
        {
            case LOW:
                subnets[1] = decryptAnnotation(key, subnets[1], cipher);
                break;
            case MEDIUM:
                subnets[2] = decryptAnnotation(key, subnets[2], cipher);
                break;
            case HIGH:
                subnets[3] = decryptAnnotation(key, subnets[3], cipher);
                break;
        }
        decryptedValue = String.join(".", subnets);
        return decryptedValue;
    }

    private String decryptPath(String key, String encryptedValue, Cipher cipher, String level)
    {
        String[] subpaths = encryptedValue.split(FILE_SEPARATOR, 5);
        int numpaths = subpaths.length;
        String decryptedValue;
        switch (level)
        {
            case LOW:
                if (numpaths > 2)
                {
                    subpaths[2] = decryptAnnotation(key, subpaths[2], cipher);
                }
                break;
            case MEDIUM:
                if (numpaths > 3)
                {
                    subpaths[3] = decryptAnnotation(key, subpaths[3], cipher);
                }
                break;
            case HIGH:
                if (numpaths > 4)
                {
                    subpaths[4] = decryptAnnotation(key, subpaths[4], cipher);
                }
                break;
        }
        decryptedValue = String.join(FILE_SEPARATOR, subpaths);
        return decryptedValue;
    }


    private String decryptAnnotation(String key, String encryptedValue, Cipher cipher)
    {
        if (encryptedValue == null)
            return null;
        if (cipher == null)
            return encryptedValue;
        try
        {
            byte[] decryptedValue = cipher.doFinal(Hex.decodeHex(encryptedValue.toCharArray()));
            return new String(decryptedValue, StandardCharsets.UTF_8);
        }
        catch (Exception ex)
        {
            String message = "Unable to decrypt value " + "'" + encryptedValue + "' of " +
                    "key '" + key + "'. ";
            logger.log(Level.SEVERE, message, ex);
        }
        return encryptedValue;
    }

    private void decryptAnnotations(AbstractVertex vertex, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String encryptedValue = vertex.getAnnotation(key);
            String decryptedValue;
            if (encryptedValue != null)
            {
                List<String> functions = functionMap.get(key);
                if (functions != null)
                {
                    String decryptMethod = functions.get(1);
                    Method method;
                    try
                    {
                        method = ABE.class.getDeclaredMethod(decryptMethod, String.class, String.class,
                                Cipher.class, String.class);
                        decryptedValue = (String) method.invoke(this, key, encryptedValue, cipher, level);
                    }
                    catch (Exception ex)
                    {
                        logger.log(Level.SEVERE, null, ex);
                        decryptedValue = encryptedValue;
                    }
                }
                else
                {
                    decryptedValue = decryptAnnotation(key, encryptedValue, cipher);
                }
                vertex.addAnnotation(key, decryptedValue);
            }
        }
    }

    private void decryptVertex(AbstractVertex vertex, Ciphers ciphers)
    {
        decryptAnnotations(vertex, this.highAnnotations, ciphers.high, HIGH);
        decryptAnnotations(vertex, this.mediumAnnotations, ciphers.medium, MEDIUM);
        decryptAnnotations(vertex, this.lowAnnotations, ciphers.low, LOW);
    }

    private void decryptVertices(ABEGraph graph, Ciphers ciphers)
    {
        for (AbstractVertex vertex : graph.vertexSet())
        {
            decryptVertex(vertex, ciphers);
        }
    }

    private String decryptTime(String key, String encryptedValue, Cipher cipher, String level)
    {
        // parse individual units of time the timestamp
        // time format is 'yyyy-MM-dd HH:mm:ss.SSS'
        String regex = "[:\\-. ]";
        String[] split = encryptedValue.split(regex);
        String year = split[0];
        String month = split[1];
        String day = split[2];
        String hour = split[3];
        String minute = split[4];
        String second = split[5];
        String millisecond = split[6];

        switch (level)
        {
            case HIGH:
                day = decryptAnnotation(key, day, cipher);
                break;
            case MEDIUM:
                hour = decryptAnnotation(key, hour, cipher);
                break;
            case LOW:
                minute = decryptAnnotation(key, minute, cipher);
                second = decryptAnnotation(key, second, cipher);
                millisecond = decryptAnnotation(key, millisecond, cipher);
                break;
        }

        // stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
        String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
                minute + ":" + second + "." + millisecond;
        return timestamp;
    }

    private void decryptAnnotations(AbstractEdge edge, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String encryptedValue = edge.getAnnotation(key);
            String decryptedValue;
            if (encryptedValue != null)
            {
                List<String> functions = functionMap.get(key);
                if (functions != null)
                {
                    String decryptMethod = functions.get(1);
                    Method method;
                    try
                    {
                        method = ABE.class.getDeclaredMethod(decryptMethod, String.class, String.class,
                                Cipher.class, String.class);
                        decryptedValue = (String) method.invoke(this, key, encryptedValue, cipher, level);
                    }
                    catch (Exception ex)
                    {
                        logger.log(Level.SEVERE, null, ex);
                        decryptedValue = encryptedValue;
                    }
                }
                else
                {
                    decryptedValue = decryptAnnotation(key, encryptedValue, cipher);
                }
                edge.addAnnotation(key, decryptedValue);
            }

        }
    }

    private void decryptEdge(AbstractEdge edge, Ciphers ciphers)
    {
        decryptAnnotations(edge, this.highAnnotations, ciphers.high, HIGH);
        decryptAnnotations(edge, this.mediumAnnotations, ciphers.medium, MEDIUM);
        decryptAnnotations(edge, this.lowAnnotations, ciphers.low, LOW);
    }

    private void decryptEdges(ABEGraph graph, Ciphers ciphers)
    {
        for (AbstractEdge edge : graph.edgeSet())
        {
            decryptEdge(edge, ciphers);
        }
    }

    private ABEGraph decryptGraph(ABEGraph graph, SecretKeys symmetricKeys)
    {
        ABEGraph decryptedGraph = ABEGraph.copy(graph, false);
        try
        {
            // initialize ciphers for decryption
            Ciphers ciphers = new Ciphers();
            if (symmetricKeys.high != null)
            {
                ciphers.high = Cipher.getInstance(ALGORITHM);
                ciphers.high.init(Cipher.DECRYPT_MODE, symmetricKeys.high);
            }
            if (symmetricKeys.medium != null)
            {
                ciphers.medium = Cipher.getInstance(ALGORITHM);
                ciphers.medium.init(Cipher.DECRYPT_MODE, symmetricKeys.medium);
            }
            if (symmetricKeys.low != null)
            {
                ciphers.low = Cipher.getInstance(ALGORITHM);
                ciphers.low.init(Cipher.DECRYPT_MODE, symmetricKeys.low);
            }

            decryptVertices(decryptedGraph, ciphers);
            decryptEdges(decryptedGraph, ciphers);
            return decryptedGraph;
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to initialize ciphers for decryption!", ex);
            return null;
        }
    }

    private SecretKey decryptKey(String encryptedKey, String level)
    {
        try
        {
            // write encrypted key to a file temporarily
            String encryptedKeyFileName = "key.cpabe";
            File encryptedKeyFile = new File(KEYS_DIR.getAbsolutePath() + FILE_SEPARATOR + encryptedKeyFileName);
            FileUtils.writeStringToFile(encryptedKeyFile, encryptedKey, StandardCharsets.UTF_8);

            // perform ABE decryption
            String keyFileName = "key.txt";
            String decryptionKeyFileName = level + ".key";
            String decryptionKeyFilePath = KEYS_DIR.getAbsolutePath() + FILE_SEPARATOR + decryptionKeyFileName;
            String command = "oabe_dec -s CP -p spade -k " + decryptionKeyFilePath + " -i " + encryptedKeyFileName +
                    " -o " + keyFileName;
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(command, null, KEYS_DIR);
            process.waitFor();
            encryptedKeyFile.delete();

            // check for errors
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            if (process.exitValue() != 0)
            {
                logger.log(Level.SEVERE, "Key decryption not successful! " +
                        "Here are the errors...");
                String errors;
                while ((errors = stdError.readLine()) != null)
                {
                    logger.log(Level.SEVERE, errors);
                }
                return null;
            }

            // read decrypted key from file
            File keyFile = new File(KEYS_DIR.getAbsolutePath() + FILE_SEPARATOR + keyFileName);
            String decryptedKey = FileUtils.readFileToString(keyFile, StandardCharsets.UTF_8);
            keyFile.delete();

            // decode and reconstruct
            byte[] secretKeyBytes = Hex.decodeHex(decryptedKey.toCharArray());
            return new SecretKeySpec(secretKeyBytes, 0, secretKeyBytes.length, BASE_ALGORITHM);
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE,
                    "Error decrypting symmetric key ", ex);
            return null;
        }
    }

    private SecretKeys decryptSymmetricKeys(ABEGraph graph)
    {
        SecretKeys secretKeys = new SecretKeys();
        secretKeys.high = decryptKey(graph.getHighKey(), HIGH);
        secretKeys.medium = decryptKey(graph.getMediumKey(), MEDIUM);
        secretKeys.low = decryptKey(graph.getLowKey(), LOW);
        return secretKeys;
    }

    public ABEGraph decryptGraph(ABEGraph graph)
    {
        SecretKeys symmetricKeys = decryptSymmetricKeys(graph);
        return decryptGraph(graph, symmetricKeys);
    }

    private String encryptAnnotation(String key, String plainValue, Cipher cipher)
    {
        if (plainValue == null)
            return null;
        try
        {
            String encryptedValueStr;
            byte[] encryptedAnnotation = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));
            encryptedValueStr = Hex.encodeHexString(encryptedAnnotation);
            return encryptedValueStr;
        }
        catch (Exception ex)
        {
            String message = "Unable to encrypt value " + "'" + plainValue + "' of " +
                    "key '" + key + "'. " +
                    "This would disturb any further encryption of annotations.";
            logger.log(Level.WARNING, message, ex);
            return plainValue;
        }
    }

    private String encryptKey(SecretKey symmetricKey, String level)
    {
        try
        {
            // write secret key to a file temporarily
            String keyFileName = "key.txt";
            String encryptedKeyFileName = "key.cpabe";
            String encodedKey = Hex.encodeHexString(symmetricKey.getEncoded());
            File keyFile = new File(KEYS_DIR.getAbsolutePath() + FILE_SEPARATOR + keyFileName);
            FileUtils.writeStringToFile(keyFile, encodedKey, StandardCharsets.UTF_8);

            // perform ABE encryption
            String command = "oabe_enc -s CP -p spade -e (" + level + ") -i " + keyFileName +
                    " -o " + encryptedKeyFileName;
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(command, null, KEYS_DIR);
            process.waitFor();
            keyFile.delete();

            // check for errors
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            if (process.exitValue() != 0)
            {
                logger.log(Level.SEVERE, "Encryption of " + level + " key not successful! " +
                        "Here are the errors...");
                String errors;
                while ((errors = stdError.readLine()) != null)
                {
                    logger.log(Level.SEVERE, errors);
                }
                return null;
            }

            // read encrypted key from file
            File encryptedKeyFile = new File(KEYS_DIR.getAbsolutePath() + FILE_SEPARATOR + encryptedKeyFileName);
            String encryptedKey = FileUtils.readFileToString(encryptedKeyFile, StandardCharsets.UTF_8);
            encryptedKeyFile.delete();

            return encryptedKey;
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Error encrypting " + level + " symmetric key", ex);
            return null;
        }
    }

    // encrypt the symmetric keys as per ABE
    private void encryptSymmetricKeys(SecretKeys symmetricKeys, ABEGraph graph)
    {
        String low = encryptKey(symmetricKeys.low, LOW);
        graph.setLowKey(low);

        String medium = encryptKey(symmetricKeys.medium, MEDIUM);
        graph.setMediumKey(medium);

        String high = encryptKey(symmetricKeys.high, HIGH);
        graph.setHighKey(high);
    }

    private String encryptIpAddress(String key, String plainValue, String level)
    {
        String[] subnets = plainValue.split("\\.");
        String encryptedValue;
        switch (level)
        {
            case LOW:
                subnets[1] = encryptAnnotation(key, subnets[1], this.cipher.low);
                break;
            case MEDIUM:
                subnets[2] = encryptAnnotation(key, subnets[2], this.cipher.medium);
                break;
            case HIGH:
                subnets[3] = encryptAnnotation(key, subnets[3], this.cipher.high);
                break;
        }
        encryptedValue = String.join(".", subnets);
        return encryptedValue;
    }

    private String encryptPath(String key, String plainValue, String level)
    {
        String[] subpaths = plainValue.split(FILE_SEPARATOR, 5);
        String encryptedValue;
        int numpaths = subpaths.length;
        switch (level)
        {
            case LOW:
                if (numpaths > 2)
                {
                    subpaths[2] = encryptAnnotation(key, subpaths[2], this.cipher.low);
                }
                break;
            case MEDIUM:
                if (numpaths > 3)
                {
                    subpaths[3] = encryptAnnotation(key, subpaths[3], this.cipher.medium);
                }
                break;
            case HIGH:
                if (numpaths > 4)
                {
                    subpaths[4] = encryptAnnotation(key, subpaths[4], this.cipher.high);
                }
                break;
        }
        encryptedValue = String.join(FILE_SEPARATOR, subpaths);
        return encryptedValue;
    }

    private String encryptTime(String key, String plainValue, String level)
    {
        // parse individual units of time the timestamp
        // time format is 'yyyy-MM-dd HH:mm:ss.SSS'
        String regex = "[:\\-. ]";
        String[] split = plainValue.split(regex);
        String year = split[0];
        String month = split[1];
        String day = split[2];
        String hour = split[3];
        String minute = split[4];
        String second = split[5];
        String millisecond = split[6];

        switch (level)
        {
            case HIGH:
                day = encryptAnnotation(key, day, this.cipher.high);
                break;
            case MEDIUM:
                hour = encryptAnnotation(key, hour, this.cipher.medium);
                break;
            case LOW:
                minute = encryptAnnotation(key, minute, this.cipher.low);
                second = encryptAnnotation(key, second, this.cipher.low);
                millisecond = encryptAnnotation(key, millisecond, this.cipher.low);
                break;
        }

        // stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
        String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
                minute + ":" + second + "." + millisecond;

        return timestamp;
    }

    private void encryptAnnotations(AbstractVertex vertex, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String plainValue = vertex.getAnnotation(key);
            String encryptedValue;
            if (plainValue != null)
            {
                List<String> functions = functionMap.get(key);
                if (functions != null)
                {
                    String encryptMethod = functions.get(0);
                    Method method;
                    try
                    {
                        method = ABE.class.getDeclaredMethod(encryptMethod, String.class, String.class, String.class);
                        encryptedValue = (String) method.invoke(this, key, plainValue, level);
                    }
                    catch (Exception ex)
                    {
                        logger.log(Level.SEVERE, null, ex);
                        encryptedValue = plainValue;
                    }
                }
                else
                {
                    encryptedValue = encryptAnnotation(key, plainValue, cipher);
                }
                vertex.addAnnotation(key, encryptedValue);
            }
        }
    }

    private void encryptAnnotations(AbstractEdge edge, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String plainValue = edge.getAnnotation(key);
            String encryptedValue;
            if (plainValue != null)
            {
                List<String> functions = functionMap.get(key);
                if (functions != null)
                {
                    String encryptMethod = functions.get(0);
                    Method method = null;
                    try
                    {
                        method = ABE.class.getDeclaredMethod(encryptMethod, String.class, String.class, String.class);
                        encryptedValue = (String) method.invoke(this, key, plainValue, level);
                    }
                    catch (Exception ex)
                    {
                        logger.log(Level.SEVERE, null, ex);
                        encryptedValue = plainValue;
                    }
                }
                else
                {
                    encryptedValue = encryptAnnotation(key, plainValue, cipher);
                }
                edge.addAnnotation(key, encryptedValue);
            }
        }
    }

    @Override
    public ABEGraph transform(Graph graph, QueryMetaData queryMetaData)
    {
        ABEGraph encryptedGraph = ABEGraph.copy(graph, true);
        Set<AbstractVertex> endPoints = new HashSet<>();
        for (AbstractEdge edge : encryptedGraph.edgeSet())
        {
            endPoints.add(edge.getChildVertex());
            endPoints.add(edge.getParentVertex());
        }
        endPoints.addAll(encryptedGraph.vertexSet());

        // generate 3 symmetric keys
        SecretKeys symmetricKeys = generateSymmetricKeys();
        if (symmetricKeys == null)
        {
            logger.log(Level.SEVERE, "Unable to encrypt data");
            return null;
        }
        try
        {
            // encrypt data
            this.cipher.low.init(Cipher.ENCRYPT_MODE, symmetricKeys.low);
            this.cipher.medium.init(Cipher.ENCRYPT_MODE, symmetricKeys.medium);
            this.cipher.high.init(Cipher.ENCRYPT_MODE, symmetricKeys.high);
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to initialize ciphers for encryption!");
        }

        encryptVertices(endPoints);
        encryptEdges(encryptedGraph.edgeSet());

        // encrypt the symmetric keys as per ABE
        encryptSymmetricKeys(symmetricKeys, encryptedGraph);
        return encryptedGraph;
    }

    private void encryptEdges(Set<AbstractEdge> edgeSet)
    {
        for (AbstractEdge edge : edgeSet)
        {
            encryptEdge(edge);
        }
    }

    private void encryptEdge(AbstractEdge edge)
    {
        encryptAnnotations(edge, highAnnotations, this.cipher.high, HIGH);
        encryptAnnotations(edge, mediumAnnotations, this.cipher.medium, MEDIUM);
        encryptAnnotations(edge, lowAnnotations, this.cipher.low, LOW);
    }

    private void encryptVertex(AbstractVertex vertex)
    {
        encryptAnnotations(vertex, this.highAnnotations, this.cipher.high, HIGH);
        encryptAnnotations(vertex, this.mediumAnnotations, this.cipher.medium, MEDIUM);
        encryptAnnotations(vertex, this.lowAnnotations, this.cipher.low, LOW);
    }

    private void encryptVertices(Set<AbstractVertex> vertexSet)
    {
        for (AbstractVertex vertex : vertexSet)
        {
            encryptVertex(vertex);
        }
    }
}
