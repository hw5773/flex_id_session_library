package versatile.flexidsession;

import versatile.flexid.FlexID;

import java.net.*;

public class FlexIDServerSocket {
	private ServerSocket server;
	
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
	String FlexIDToIPAddress(FlexID address) {
		String addr = "127.0.0.1"; // Get the IP address from a FlexIDManager.
		return addr;
	}
	FlexIDSocket accept() {
		try {
			Socket sock = server.accept();
			FlexIDSocket socket = new FlexIDSocket(sock);
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

	public InetAddress getInetAddress() {
		return server.getInetAddress();
	}

	public int getPort() {
		return server.getLocalPort();
	}
}
