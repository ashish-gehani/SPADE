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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.ABEGraph;
import spade.utility.ABEGraph.AnnotationValue;
import spade.utility.ABEGraph.EncryptedEdge;
import spade.utility.ABEGraph.EncryptedVertex;

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
    private static final String ENCRYPT_METHOD = "encrypt";
    private static final String DECRYPT_METHOD = "decrypt";
    private static final String CLASS_PREFIX = "spade.utility.ABEGraph$";
    private File KEYS_DIR;
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final Logger logger = Logger.getLogger(ABE.class.getName());

    private final List<String> lowAnnotations = new ArrayList<>();
    private final List<String> mediumAnnotations = new ArrayList<>();
    private final List<String> highAnnotations = new ArrayList<>();
    private final Map<String, String> classMap = new HashMap<>();

    private static class SecretKeys
    {
        SecretKey low;
        SecretKey medium;
        SecretKey high;
    }

    private static class Ciphers
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
            List<String> lines = FileUtils.readLines(new File(configFileName), StandardCharsets.UTF_8);
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
                        if(split.length != 2)
                        {
                            logger.log(Level.SEVERE, "incorrect format for keys directory!");
                            return false;
                        }
                        keyDirectoryPath = split[1].trim();
                    }
                    else
                    {
                        if(level == null)
                        {
                            logger.log(Level.SEVERE, "encryption level not provided");
                            return false;
                        }
                        List<String> annotations = new ArrayList<>();
                        String[] annotationsList = line.split(",");
                        if(annotationsList.length <= 0)
                        {
                            logger.log(Level.SEVERE, "incorrect format for annotations!");
                            return false;
                        }
                        for (String annotation : annotationsList)
                        {
                            String cleanAnnotation = annotation.trim();
                            String substr = StringUtils.substringBetween(cleanAnnotation, "[", "]");
                            if (substr != null)
                            {
                                cleanAnnotation = cleanAnnotation.substring(0, cleanAnnotation.indexOf("[")).trim();
                                classMap.put(cleanAnnotation, substr.trim());
                            }
                            annotations.add(cleanAnnotation);
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
            	keyDirectoryPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(keyDirectoryPath);
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

    public static String decryptAnnotation(String key, String encryptedValue, Cipher cipher)
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

    private void decryptAnnotations(EncryptedVertex vertex, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String encryptedValue = vertex.getAnnotation(key);
            String decryptedValue;
            if (encryptedValue != null)
            {
                String className = classMap.get(key);
                if (className != null)
                {
                    try
                    {
                        Method method;
                        className = CLASS_PREFIX + className;
                        Class<?> decryptionClass = Class.forName(className);
                        Constructor<?> classConstructor = decryptionClass.getDeclaredConstructor(String.class);
                        Object decryptionClassObj = classConstructor.newInstance(encryptedValue);
                        method = decryptionClass.getDeclaredMethod(DECRYPT_METHOD, String.class, String.class,
                                String.class, Cipher.class);
                        decryptedValue = (String) method.invoke(decryptionClassObj, key, encryptedValue, level, cipher);
                        vertex.addAnnotation(key, (AnnotationValue) decryptionClassObj);
                    }
                    catch (Exception ex)
                    {
                        // In case the decryption class is missing
                        // Put the encryption string and flag
                        logger.log(Level.SEVERE, "Decryption class not found for key '" + key + "'!", ex);
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

    private void decryptVertex(EncryptedVertex vertex, Ciphers ciphers)
    {
        decryptAnnotations(vertex, this.highAnnotations, ciphers.high, HIGH);
        decryptAnnotations(vertex, this.mediumAnnotations, ciphers.medium, MEDIUM);
        decryptAnnotations(vertex, this.lowAnnotations, ciphers.low, LOW);
    }

    private void decryptVertices(ABEGraph graph, Ciphers ciphers)
    {
        for (AbstractVertex vertex : graph.vertexSet())
        {
            decryptVertex((EncryptedVertex) vertex, ciphers);
        }
    }

    private void decryptAnnotations(EncryptedEdge edge, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String encryptedValue = edge.getAnnotation(key);
            String decryptedValue;
            if (encryptedValue != null)
            {
                String className = classMap.get(key);
                if (className != null)
                {
                    try
                    {
                        Method method;
                        className = CLASS_PREFIX + className;
                        Class<?> decryptionClass = Class.forName(className);
                        Constructor<?> classConstructor = decryptionClass.getDeclaredConstructor(String.class);
                        Object decryptionClassObj = classConstructor.newInstance(encryptedValue);
                        method = decryptionClass.getDeclaredMethod(DECRYPT_METHOD, String.class, String.class,
                                String.class, Cipher.class);
                        decryptedValue = (String) method.invoke(decryptionClassObj, key,
                                encryptedValue, level, cipher);
                        edge.addAnnotation(key, (AnnotationValue) decryptionClassObj);
                    }
                    catch (Exception ex)
                    {
                        // In case the decryption class is missing
                        // Put the encryption string and flag
                        logger.log(Level.SEVERE, "Decryption class not found for key '" + key + "'!", ex);
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

    private void decryptEdge(EncryptedEdge edge, Ciphers ciphers)
    {
        decryptAnnotations(edge, this.highAnnotations, ciphers.high, HIGH);
        decryptAnnotations(edge, this.mediumAnnotations, ciphers.medium, MEDIUM);
        decryptAnnotations(edge, this.lowAnnotations, ciphers.low, LOW);
    }

    private void decryptEdges(ABEGraph graph, Ciphers ciphers)
    {
        for (AbstractEdge edge : graph.edgeSet())
        {
            decryptEdge((EncryptedEdge) edge, ciphers);
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

    public static String encryptAnnotation(String key, String plainValue, Cipher cipher)
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
                    "This might disturb any further encryption of annotations.";
            logger.log(Level.WARNING, message, ex);
            return plainValue;
        }
    }

    private String encryptKey(SecretKey symmetricKey, String level)
    {
        try
        {
            // write secret key to a file temporarily
            final String keyFilePath = Settings.getPathRelativeToTemporaryDirectory("key.txt");
            final String encryptedKeyFilePath = Settings.getPathRelativeToTemporaryDirectory("key.cpabe");

            final String encodedKey = Hex.encodeHexString(symmetricKey.getEncoded());

            final File keyFile = new File(keyFilePath);
            FileUtils.writeStringToFile(keyFile, encodedKey, StandardCharsets.UTF_8);

            // perform ABE encryption
            String command = "oabe_enc -s CP -p spade -e (" + level + ") -i " + keyFilePath +
                    " -o " + encryptedKeyFilePath;
            //logger.log(Level.INFO, "Command: " + Arrays.asList(command.split("\\s+")));
            ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
            pb.environment().put("LD_LIBRARY_PATH", "/usr/local/lib/");
            pb.directory(this.KEYS_DIR);
            //Runtime runtime = Runtime.getRuntime();
            //Process process = runtime.exec(command, null, KEYS_DIR);
            Process process = pb.start();
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
            
            /*
			BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while((line = stdOut.readLine()) != null){
				logger.log(Level.INFO, "Enc out: " + line);
			}
			*/

            // read encrypted key from file
            File encryptedKeyFile = new File(encryptedKeyFilePath);
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

    private void encryptAnnotations(EncryptedVertex vertex, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String plainValue = vertex.getAnnotation(key);
            String encryptedValue;
            if (plainValue != null)
            {
                String className = classMap.get(key);
                if (className != null)
                {
                    try
                    {
                        Method method;
                        className = CLASS_PREFIX + className;
                        Class<?> encryptionClass = Class.forName(className);
                        Constructor<?> classConstructor = encryptionClass.getDeclaredConstructor(String.class);
                        Object encryptionClassObj = classConstructor.newInstance(plainValue);
                        method = encryptionClass.getDeclaredMethod(ENCRYPT_METHOD, String.class, String.class,
                                String.class, Cipher.class);
                        encryptedValue = (String) method.invoke(encryptionClassObj, key, plainValue, level, cipher);
                        vertex.addAnnotation(key, (AnnotationValue) encryptionClassObj);
                    }
                    catch (Exception ex)
                    {
                        // In case the encryption class is missing
                        // Put the plain string and flag
                        logger.log(Level.SEVERE, "Encryption class not found for key '" + key + "'!", ex);
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

    private void encryptAnnotations(EncryptedEdge edge, List<String> keys, Cipher cipher, String level)
    {
        for (String key : keys)
        {
            String plainValue = edge.getAnnotation(key);
            String encryptedValue;
            if (plainValue != null)
            {
                String className = classMap.get(key);
                if (className != null)
                {
                    try
                    {
                        Method method;
                        className = CLASS_PREFIX + className;
                        Class<?> encryptionClass = Class.forName(className);
                        Constructor<?> classConstructor = encryptionClass.getDeclaredConstructor(String.class);
                        Object encryptionClassObj = classConstructor.newInstance(plainValue);
                        method = encryptionClass.getDeclaredMethod(ENCRYPT_METHOD, String.class, String.class,
                                String.class, Cipher.class);
                        encryptedValue = (String) method.invoke(encryptionClassObj, key, plainValue, level, cipher);
                        edge.addAnnotation(key, (AnnotationValue) encryptionClassObj);
                    }
                    catch (Exception ex)
                    {
                        // In case the encryption class is missing
                        // Put the plain string and flag
                        logger.log(Level.SEVERE, "Encryption class not found for key '" + key + "'!", ex);
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

        // generate 3 symmetric keys
        SecretKeys symmetricKeys = generateSymmetricKeys();
        if (symmetricKeys == null)
        {
            logger.log(Level.SEVERE, "Unable to encrypt data");
            return null;
        }
        try
        {
            this.cipher.low.init(Cipher.ENCRYPT_MODE, symmetricKeys.low);
            this.cipher.medium.init(Cipher.ENCRYPT_MODE, symmetricKeys.medium);
            this.cipher.high.init(Cipher.ENCRYPT_MODE, symmetricKeys.high);
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to initialize ciphers for encryption!");
        }

        // encrypt data
//        encryptVertices(endPoints);
        encryptVertices(encryptedGraph.vertexSet());
        encryptEdges(encryptedGraph.edgeSet());

        // encrypt the symmetric keys as per ABE
        encryptSymmetricKeys(symmetricKeys, encryptedGraph);
        return encryptedGraph;
    }

    private void encryptEdge(EncryptedEdge edge)
    {
        encryptAnnotations(edge, highAnnotations, this.cipher.high, HIGH);
        encryptAnnotations(edge, mediumAnnotations, this.cipher.medium, MEDIUM);
        encryptAnnotations(edge, lowAnnotations, this.cipher.low, LOW);
    }

    private void encryptEdges(Set<AbstractEdge> edgeSet)
    {
        for (AbstractEdge edge : edgeSet)
        {
            encryptEdge((EncryptedEdge) edge);
        }
    }

    private void encryptVertex(EncryptedVertex vertex)
    {
        encryptAnnotations(vertex, this.highAnnotations, this.cipher.high, HIGH);
        encryptAnnotations(vertex, this.mediumAnnotations, this.cipher.medium, MEDIUM);
        encryptAnnotations(vertex, this.lowAnnotations, this.cipher.low, LOW);
    }

    private void encryptVertices(Set<AbstractVertex> vertexSet)
    {
        for (AbstractVertex vertex : vertexSet)
        {
            encryptVertex((EncryptedVertex) vertex);
        }
    }
}
