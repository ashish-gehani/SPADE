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
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
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
public class AuthSSLClientSocket extends CipherSocket {

    private String serverString = "server";
    private String clientString = "client";
    private int defaultTimeout = Integer.parseInt(Settings.getProperty("connection_timeout"));
    static final Logger logger = Logger.getLogger(AuthSSLClientSocket.class.getName());

    public AuthSSLClientSocket(String host, int port, String sharedSecret) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        super(sharedSecret);

        InetSocketAddress sockaddr = new InetSocketAddress(host, port);
        this.connect(sockaddr, defaultTimeout);

        PrintStream outputStream = new PrintStream(this.getOutputStream());
        String encryptedMessage = readLine(this.getInputStream());

        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, this.getKey());
            byte[] decodedValue = new BASE64Decoder().decodeBuffer(encryptedMessage);
            byte[] decryptedBytes = cipher.doFinal(decodedValue);
            String decryptedValue = new String(decryptedBytes);
            String[] tokens = decryptedValue.split(":");
            int messageNumber = Integer.parseInt(tokens[0]);
            String messageTime = tokens[1];
            String messageServer = tokens[2];
            if (messageServer.equals(serverString)) {
                messageNumber++;
                String responseText = messageNumber + ":" + messageTime + ":" + clientString;
                cipher.init(Cipher.ENCRYPT_MODE, this.getKey());
                byte[] encryptedBytes = cipher.doFinal(responseText.getBytes());
                String encryptedValue = new BASE64Encoder().encode(encryptedBytes);
                outputStream.println(encryptedValue);
                this.enableCipher(true);
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
            AuthSSLClientSocket s = new AuthSSLClientSocket("localhost", 1340, "abc123");
            System.out.println("Connected with the server");

            PrintStream printstream = new PrintStream(s.getOutputStream());
            printstream.println("Hellow from client1!");
            printstream.println("Hellow from client2!");

            BufferedReader inputStream = new BufferedReader(new InputStreamReader(s.getInputStream()));
            System.out.println("Received: " + inputStream.readLine());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
