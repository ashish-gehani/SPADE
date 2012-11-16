/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * This class provides a socket with authentication using pre-shared key and
 * provides subsequent communication over secure channel Since there is no
 * TLS-PKS in Java right now, we simply make a secure connection and
 * authenticate by sending the shared key and matching it
 *
 * @author Sharjeel Ahmed Qureshi
 */
public class AuthSSLServerSocket extends ServerSocket {

    private String sharedSecret;
    private String serverString = "server";
    private String clientString = "client";
    static final Logger logger = Logger.getLogger(AuthSSLServerSocket.class.getName());

    public AuthSSLServerSocket(int port, String sharedSecret) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        super(port);
        this.sharedSecret = sharedSecret;
    }

    @Override
    public Socket accept() throws IOException {
        CipherSocket socket = new CipherSocket(sharedSecret);
        SecretKey secretKey = socket.getKey();
        implAccept(socket);
        PrintStream outputStream = new PrintStream(socket.getOutputStream());

        // Send authentication string
        String time = Long.toString(System.currentTimeMillis());
        Random generator = new Random();
        int randomInt = generator.nextInt();
        String plaintext = randomInt + ":" + time + ":" + serverString;
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes());
            String encryptedValue = new BASE64Encoder().encode(encryptedBytes);
            outputStream.println(encryptedValue);
        } catch (BadPaddingException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalBlockSizeException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (NoSuchPaddingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        // Wait for correct response
        String encryptedResponse = readLine(socket.getInputStream());
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedValue = new BASE64Decoder().decodeBuffer(encryptedResponse);
            byte[] decryptedBytes = cipher.doFinal(decodedValue);
            String decryptedValue = new String(decryptedBytes);
            String[] tokens = decryptedValue.split(":");
            int responseNumber = Integer.parseInt(tokens[0]);
            String responseTime = tokens[1];
            String responseClient = tokens[2];
            if (responseNumber == (randomInt + 1) && responseTime.equals(time) && responseClient.equals(clientString)) {
                socket.enableCipher(true);
                return socket;
            }
        } catch (BadPaddingException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalBlockSizeException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (NoSuchPaddingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        throw new IOException("auth failed");
    }

    private String readLine(InputStream inputStream) {
        StringBuilder tempString = new StringBuilder();
        try {
            while (true) {
                int c = inputStream.read();
                if ((char) c == '\n' || (c == -1)) {
                    break;
                } else {
                    tempString.append((char) c);
                }
            }
            return tempString.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    public static void main(String[] argc) {
        try {
            AuthSSLServerSocket asock = new AuthSSLServerSocket(1340, "abc123");
            Socket s = asock.accept();
            System.out.println("Client connected");

            BufferedReader inputStream = new BufferedReader(new InputStreamReader(s.getInputStream()));
            System.out.println("Received 1: " + inputStream.readLine());
            System.out.println("Received 2: " + inputStream.readLine());

            PrintStream out = new PrintStream(s.getOutputStream());
            out.println("This is a response from server");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}
