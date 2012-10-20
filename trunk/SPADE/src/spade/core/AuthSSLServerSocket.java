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
import java.net.ServerSocket;
import java.io.*;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * This class provides a socket with authentication using
 * pre-shared key and provides subsequent communication over secure channel
 * Since there is no TLS-PKS in Java right now, we simply make a secure  
 * connection and authenticate by sending the shared key and matching it
 *
 * @author Sharjeel Ahmed Qureshi
 */

public class AuthSSLServerSocket extends ServerSocket {
	private String sharedSecret;
	
	public AuthSSLServerSocket (int port, String sharedSecret) throws IOException {
		super(port);
	}
}

/** TODO: Fix this class 
public class AuthSSLServerSocket extends ServerSocket {

	private SSLServerSocket _sslsocket;
	private String sharedSecret;
	
	public AuthSSLServerSocket (int port, String sharedSecret) throws IOException {
		_sslsocket = ((SSLServerSocket) ((SSLServerSocketFactory) SSLServerSocketFactory.getDefault()).createServerSocket(port));
		_sslsocket.setEnabledCipherSuites(new String[]{"TLS_DH_anon_WITH_AES_128_CBC_SHA"});
		this.sharedSecret = sharedSecret;
	}
	
	@Override
	public Socket accept() throws IOException {
		Socket sock = _sslsocket.accept();
		BufferedReader inputStream = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		PrintStream outStream = new PrintStream(sock.getOutputStream());
		String clientkey = inputStream.readLine();	
		if ( !clientkey.equals(sharedSecret) ) { 
			outStream.println("denied");
		    throw new IOException("Client Authentication failed");
		}
		outStream.println("ok");
		// outStream.close();
		// inputStream.close();
		return sock;

	}
	
	@Override
	public void close() throws IOException {
		_sslsocket.close();
	}
	
	public static void main(String[] argc) {
		try {
			AuthSSLServerSocket asock = new AuthSSLServerSocket(1340, "abc123");
			Socket s = asock.accept();
			
			System.out.println("Client connected");			
			
			PrintStream out = new PrintStream(s.getOutputStream());
			BufferedReader inputStream = new BufferedReader(new InputStreamReader(s.getInputStream()));
			System.out.println("Received: " + inputStream.readLine());
			out.println("This is a response from server");
		} catch( Exception e) {
			e.printStackTrace();
		}
	}
}
***/