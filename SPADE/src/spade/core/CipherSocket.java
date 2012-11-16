/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.spec.KeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author dawood
 */
public class CipherSocket extends Socket {

    private SecretKey secretKey;
    private boolean enableCipher = true;
    private final String salt = "12345678";
    private final String iv = "abcd1234efgd5678";
    private final String cipherMode = "AES/CFB8/NoPadding";
    static final Logger logger = Logger.getLogger(CipherSocket.class.getName());

    public void enableCipher(boolean enabled) {
        enableCipher = enabled;
    }

    public void setKey(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public SecretKey getKey() {
        return secretKey;
    }

    public CipherSocket() {
        super();
    }

    public void createKey(String sharedSecret) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(sharedSecret.toCharArray(), salt.getBytes(), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            this.secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public CipherSocket(String sharedSecret) {
        super();
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(sharedSecret.toCharArray(), salt.getBytes(), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            this.secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (enableCipher) {
            Cipher cipher;
            try {
                cipher = Cipher.getInstance(cipherMode);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv.getBytes()));
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
                return null;
            }
            CipherInputStream cipherInput = new CipherInputStream(super.getInputStream(), cipher);
            return cipherInput;
        } else {
            return super.getInputStream();
        }
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (enableCipher) {
            Cipher cipher;
            try {
                cipher = Cipher.getInstance(cipherMode);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv.getBytes()));
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
                return null;
            }
            CipherOutputStream cipherOutput = new CipherOutputStream(super.getOutputStream(), cipher);
            return cipherOutput;
        } else {
            return super.getOutputStream();
        }
    }
}
