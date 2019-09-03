package versatile.flexidsession;

import java.net.*;

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
		return server.getInetAddress().getHostAddress();
//		return serverIp;
	}

	public int getPort() {
		return server.getLocalPort();
	}
}
