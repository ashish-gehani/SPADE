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

import java.net.Socket;
import java.io.*;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * This class provides a server socket with authentication using
 * pre-shared key and provides subsequent communication over secure channel
 * Since there is no TLS-PKS in Java right now, we simply make a secure  
 * connection and authenticate by sending the shared key and matching it
 *
 * @author Sharjeel Ahmed Qureshi
 */

/*
 * Phony Class for now	
 */
public class AuthSSLSocketFactory extends Socket {
	public static Socket getSocket(Socket s, String host, int port, String sharedSecret) throws IOException {
		return s;
	}
	
	public static Socket getSocket(Socket s, InetSocketAddress addr, String sharedSecret) throws IOException {
		return getSocket(s, addr.getHostName(), addr.getPort(), sharedSecret);
	}
	
	public static Socket getSocket(String host, int port, String sharedSecret) throws IOException {
		Socket sock = new Socket();
		sock.connect(new InetSocketAddress(host, port));
		return getSocket(sock, host, port, sharedSecret);
	}
}
/*** TODO: Enabel this class only after SSL's implemtntion have been checked across different JVM versions
public class AuthSSLSocketFactory {
	
	public static Socket getSocket(Socket s, String host, int port, String sharedSecret) throws IOException {

		SSLSocket _sslsocket = ((SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(s, host, port, true));
		_sslsocket.setEnabledCipherSuites(new String[]{"TLS_DH_anon_WITH_AES_128_CBC_SHA"});
		_sslsocket.setUseClientMode(true);
		
		PrintStream outStream = new PrintStream(_sslsocket.getOutputStream());
		BufferedReader inputStream = new BufferedReader(new InputStreamReader(_sslsocket.getInputStream()));
		
		outStream.println(sharedSecret);
		String resp = inputStream.readLine();
		System.out.println("Response received: " + resp);
		if (!resp.equalsIgnoreCase("ok")) {
			throw new IOException("Access Denied. Invalid Shared Key");
		}
		return _sslsocket;		
	}
	
	public static Socket getSocket(Socket s, InetSocketAddress addr, String sharedSecret) throws IOException {
		return getSocket(s, addr.getHostName(), addr.getPort(), sharedSecret);
	}
	
	public static Socket getSocket(String host, int port, String sharedSecret) throws IOException {
		Socket sock = new Socket();
		sock.connect(new InetSocketAddress(host, port));
		return getSocket(sock, host, port, sharedSecret);
	}
	
	public static void main(String[] argc) {
		try {
			Socket s = getSocket("localhost", 1340, "abc123");
			System.out.println("Connected with the server");
			PrintStream out = new PrintStream(s.getOutputStream());
			BufferedReader inputStream = new BufferedReader(new InputStreamReader(s.getInputStream()));
			out.println("Hellow from client!");	
			System.out.println("Received: " + inputStream.readLine());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
***/