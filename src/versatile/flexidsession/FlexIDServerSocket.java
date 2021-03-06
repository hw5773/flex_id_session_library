package versatile.flexidsession;

import versatile.flexid.FlexID;

import java.net.*;
import java.util.Enumeration;

public class FlexIDServerSocket {
	private ServerSocket server;
	private FlexIDSocket socket;
	
	FlexIDServerSocket() {
		try {
			server = new ServerSocket();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	FlexIDServerSocket(int port) {
		try {
			server = new ServerSocket(port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	FlexIDSocket accept() {
		try {
			Socket sock = server.accept();
			socket = new FlexIDSocket(sock);
			System.out.println("Accept success.");
			return socket;	
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	void bind(SocketAddress bindpoint) {
		try {
			server.bind(bindpoint);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	void close() {
		try {
			server.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public String getInetAddress() {
		return "147.46.216.213";
	}

	public int getPort() {
		return server.getLocalPort();
	}
}
