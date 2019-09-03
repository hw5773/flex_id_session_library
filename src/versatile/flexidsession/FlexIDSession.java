package versatile.flexidsession;

import org.json.JSONObject;
import versatile.flexid.*;

import javax.naming.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Enumeration;

public class FlexIDSession {
	private static int port = 3335;
//	int lock = 0;

	private FlexID SFID, DFID; // source & destination FlexID
	private byte[] connID;

	private int isServer;
	private boolean changed = false;
	private boolean ready = false;
    private boolean retransmission = false;
    private boolean sourceChanged = false;

	// variables to manage data sequence at the inbound & outbound function.
	private int sentSEQ = 0; // SEQ of my data
	private int recvACK = 0;
	private int recvSEQ = 0; 
	private int sentACK = 0; // send ACK for receiving data

	private CircularQueue rbuf = new CircularQueue();
	private CircularQueue wbuf = new CircularQueue();

	private FlexIDSocket socket;
	private Thread inThread, outThread;
	
	public void createConnection(FlexIDSocket sock) {
		try {
			MessageDigest sh = MessageDigest.getInstance("SHA-1");
			sh.update(SFID.getIdentity());
			sh.update(DFID.getIdentity());
			connID = sh.digest();
			System.out.println("ConnID: ");
			for (int i=0; i<connID.length; i++) {
				if (i == 10)
					System.out.println();
				System.out.print(connID[i]);
			}
			System.out.println();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		if(sock != null) {
			socket = sock;
			isServer = 1;
		} else {
			socket = new FlexIDSocket(DFID); // connect to server
			isServer = 0;
		}
	}
	public FlexIDSession(FlexID sFID, FlexID dFID, FlexIDSocket sock) {
		SFID = sFID;
		DFID = dFID;
		createConnection(sock);

		inThread = new Thread(new inbound());
		inThread.setDaemon(true);
		inThread.start();
		
		outThread = new Thread(new outbound());
		outThread.setDaemon(true);
		outThread.start();
	}
	public FlexIDSession(FlexID sFID, FlexID dFID) {
		this(sFID, dFID, null); // client case
	}
	
	public static FlexIDSession accept() {
		FlexIDServerSocket server = new FlexIDServerSocket(port);
		System.out.println("Server waits a connection.");
		FlexIDSocket sock = server.accept();
		if(sock == null) {
			System.out.println("accept failed.");
			return null;
		}

		System.out.println("Connected.");

		FlexID sFID = new FlexID();
		Locator sLoc = new Locator(InterfaceType.WIFI, sock.getInetAddress(), sock.getPort());
		System.out.println("Source IP Address: " + sock.getInetAddress() + "  Port: " + sock.getPort());
		sFID.setIdentity("0x5555".getBytes());
		sFID.setLocator(sLoc);

		FlexID dFID = new FlexID();
		Locator dLoc = new Locator(InterfaceType.ETH, server.getInetAddress(), server.getPort());
		System.out.println("Destination IP Address: " + server.getInetAddress() + "  Port: " + server.getPort());
		dFID.setIdentity("0x1111".getBytes());
		dFID.setLocator(dLoc);

		return new FlexIDSession(sFID, dFID, sock);
	}

	// send, receive: functions used in application layer
	public int send(byte[] msg) {
		return wbuf.write(msg);
	}
	public int receive(byte[] b) {
		return rbuf.read(b);
	}

	private class inbound implements Runnable {
		public void run() {
			MobilityManager mm = new MobilityManager();
			try {
				while(!inThread.isInterrupted()) {
					if (isServer == 0) {
						if (mm.checkAddress(SFID)) { // When client's ip changed
							while (changed == false) {}
							System.out.println("[FlexIDSession] We are going to access to " + DFID.getLocator().getAddr() + ":" + DFID.getLocator().getPort());
							
							socket = new FlexIDSocket(DFID);
							if (socket != null)
								System.out.println("[FlexIDSession] Connect Success");
							else
								System.out.println("[FlexIDSession] Connect Failure");
							
							changed = false;
						}
						else if (sourceChanged) { // When source changed
							while (changed == false) {}
							System.out.println("[FlexIDSession] We are going to access to " + DFID.getLocator().getAddr() + ":" + DFID.getLocator().getPort());
							
							createConnection(null);
							
							changed = false;
							sourceChanged = false;
						}
					}
					
					byte[] message;
//					if(lock != 1) {
//						lock = 1;
				
					if((message = getRecvMsg()) != null) {
						System.out.println("[Rebinding] Rebinding End Time: " + System.currentTimeMillis());
						byte[] header = getHeader(message); // length(2B) + connID(20B) + seq(4B), ack(4B)
						byte[] data = getData(message);
						byte[] msgConnID = Arrays.copyOfRange(header, 2, 22);
							
						if(!Arrays.equals(connID, msgConnID)) {
							System.out.println("connID unmatched.");
//								lock = 0;
							continue;
						}
						
						int length = Conversion.byte2ToInt(Arrays.copyOfRange(header, 0, 2)); // total length
						int msgSeq = Conversion.byte4ToInt(Arrays.copyOfRange(header, 22, 26));
						int msgAck = Conversion.byte4ToInt(Arrays.copyOfRange(header, 26, 30));
						
						if(length > 30) { // received data message.
							if(sentACK <= msgSeq) {
								recvSEQ = msgSeq;
								sentACK = msgSeq+1;
								rbuf.write(data);
							}
									
							// send ACK message.
							byte[] ACKmessage = setHeader(null);

							System.out.println("Send ACK message #: " + sentACK + " to " + DFID.getLocator().getAddr());
					
							socket.write(ACKmessage);
						}
						else { // received ACK message.
							System.out.println("Received ACK message");
							if(msgAck >= (sentSEQ+1))
								recvACK = msgAck;
						}
					}
//						lock = 0;
//					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
//				lock = 0;
			}
		}
		
		// Polling: Get msg from socket. After that, write the msg to the rbuf.
		public byte[] getRecvMsg() {
			try {
	            byte[] message = null;
	            
	            if (socket != null)
				    message = socket.read();
	            
				if(message != null) {
					System.out.println("Received) " + message.length + "  sentSEQ) " + sentSEQ + "  sentACK) " + sentACK + "  recvSEQ) " + recvSEQ + "  recvACK) " + recvACK);
					return message;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return null;
		}
	}
	private class outbound implements Runnable {
		public void run() {
			try {
				byte[] message = new byte[30];

				while(!outThread.isInterrupted()) {

					if(retransmission == true) {
						socket.write(message); // retransmit the last sent message to handle not received ACK.
						retransmission = false;
						continue;
					}

					if((recvACK != (sentSEQ+1)) && (sentSEQ != 0)) {
						continue;
					}

					if(!wbuf.isEmpty(1)) {
						byte[] data = new byte[2048];
						int dataLen = wbuf.read(data);
						System.out.println("Messages to be sent: " + dataLen);
						data = Arrays.copyOfRange(data, 0, dataLen);

						byte[] header = setHeader(data);
						message = new byte[30 + data.length];

						System.arraycopy(header, 0, message, 0, 30);
						System.arraycopy(data, 0, message, 30, data.length);

						socket.write(message);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
	}
    // Check wbuf to send msg to socket.
    public int checkMsgToSend() {
        if(wbuf.isEmpty(1)) {
            return -1;
        }
        else return 1;
    }
	
	public byte[] getHeader(byte[] message) {
		return Arrays.copyOfRange(message, 0, 30);
	}
	public byte[] setHeader(byte[] message) {
		try {
			// Header format(30Bytes) = length(2) + connID(20) + seq(4) + ack(4)
			byte[] length = new byte[2];
			byte[] seq = new byte[4];
			byte[] ack = new byte[4];
			byte[] header = new byte[30];
			
			int msgLength;
			
			if(message == null) { // ACK message 
				msgLength = 30;
				ack = Conversion.int32ToByteArray(sentACK);
			}
			else { // Data message
				msgLength = message.length + 30;
				sentSEQ += message.length;
				byte[] temp = Conversion.int32ToByteArray(sentSEQ);
				System.arraycopy(temp, 0, seq, 0, temp.length);
			}
			
			length = Conversion.int16ToByteArray(msgLength);
			System.arraycopy(length, 0, header, 0, 2);
			System.arraycopy(connID, 0, header, 2, 20);
			System.arraycopy(seq, 0, header, 22, 4);
			System.arraycopy(ack, 0, header, 26, 4);
			
			return header;
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		
		return null;
	}
	public byte[] getData(byte[] message) {
		int dataLength = Conversion.byte2ToInt(Arrays.copyOfRange(message, 0, 2)) - 30;
		byte[] data = new byte[dataLength];
		System.arraycopy(message, 30, data, 0, dataLength);
		return data;
	}

	/* Connect to peer's signal server and rebuild/terminate a session */
	class ConnectToSignalServerThread extends Thread {
		String type;
		boolean complete = false;
		FlexID newDFID;
		Socket socket = null; // connect to signal server
		public ConnectToSignalServerThread(String type) {
			this.type = type;
		}
		@Override
		public void run() {
			try {
				System.out.println("[FlexIDSession] Try to access IP: " + DFID.getLocator().getAddr() + "  Port: " + 3334);

				while (socket == null)
					socket = new Socket(DFID.getLocator().getAddr(), 3334);

				while (complete == false) {
					BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

					JSONObject request = new JSONObject();
					request.put("type",  type);
					request.put("flex_id", new String(SFID.getIdentity()));
					out.println(request);

					JSONObject response = new JSONObject(input.readLine());
					String flex_id = response.getString("flex_id");
					if(type == "reconnectACK") {
						String ip = response.getString("ip");
						int port = response.getInt("port");

						System.out.println("[FlexIDSession] Received from Signal Server) ID: " + flex_id + " / ip: " + ip + " / port: " + port);
						Locator locator = new Locator(InterfaceType.WIFI, ip, port);
						DFID.setLocator(locator);
						changed = true;
					}
					else if(type == "terminateACK") {
						System.out.println("[FlexIDSession] Received terminateACK from Signal Server.");
						DFID = newDFID;
						changed = true;
					}
					
					complete = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/* Server function */
	public void handleReconnect() {
		port = 3337; // change port.
		FlexIDServerSocket server = new FlexIDServerSocket(port);
		System.out.println("Server waits a reconnection.");

		socket = server.accept();
		System.out.println("Reconnected.");
		retransmission = true;
	}
	
	/* Client class */
	private class MobilityManager {
		boolean checkAddress(FlexID id) {
			boolean ret;
			String ip = "";

			while (ip.equals("")) {
				ip = getLocalIpAddress();
			}

			System.out.println("Previous IP Address: " + id.getLocator().getAddr());
			System.out.println("Current IP Address: " + ip);

			if (id.getLocator().getAddr().equals(ip)) {
				ret = false;
				System.out.println("The IP Address is not changed.");
			} else {
				ret = true;
				System.out.println("The IP Address is changed.");
				while (ready == false) {}
				System.out.println("[Rebinding] Rebinding Time Start: " + System.currentTimeMillis());
				id.getLocator().setAddr(ip);
				ConnectToSignalServerThread connectToSignalServerThread = new ConnectToSignalServerThread("reconnect");
				connectToSignalServerThread.start();
			}

			return ret;
		}

		String getLocalIpAddress() {
			String ip = "";

			try {
				Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();

				while (enumNetworkInterfaces.hasMoreElements()) {
					NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
					Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();

					while (enumInetAddress.hasMoreElements()) {
						InetAddress inetAddress = enumInetAddress.nextElement();

						if (inetAddress.isSiteLocalAddress()) {
							ip = inetAddress.getHostAddress();
						}
					}
				}
			} catch (SocketException e) {
				e.printStackTrace();
			}

			return ip;
		}
	}
	
	/* Client function */
	public int changeContentSource(String ip, int port) {
		try {
			sourceChanged = true;
			ConnectToSignalServerThread connectToSignalServerThread = new ConnectToSignalServerThread("terminate"); // terminate old session
			Locator locator = new Locator(InterfaceType.WIFI, ip, port);
			FlexID newDFID = new FlexID();
			newDFID.setLocator(locator);

			connectToSignalServerThread.newDFID = newDFID;
			connectToSignalServerThread.start(); // terminate present session
			
			return 1;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	public void close() {
		try {
			inThread.join();
			outThread.join();
			socket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public FlexID getSFID() {
		return SFID;
	}
	public void setSFID(FlexID sFID) {
		this.SFID = sFID;
	}
	public FlexID getDFID() {
		return DFID;
	}
	public void setDFID(FlexID dFID) {
		this.DFID = dFID;
	}
	public byte[] getConnID() {
		return connID;
	}
	public void setReady(boolean ready) {
		this.ready = ready;
	}
}


