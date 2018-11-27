package versatile.flexidsession;

import versatile.flexid.FlexID;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * FlexID socket
 * @author mckwak
 */
public class FlexIDSession implements Serializable {
	// mckwak: getter/setter, Serializable interface
	private static final long serialVersionUID = 1L;
	private static int port = 3335;
	int lock = 0;

	private FlexID SFID; // source FlexID
	private FlexID DFID; // destination FlexID
	private byte[] connID;
	
	// managed at the inbound, outbound function.
	private int sentSEQ; // SEQ of my data
	private int recvACK;
	private int recvSEQ; 
	private int sentACK; // send ACK for receiving data
	
	
	private CircularQueue rbuf;
	private CircularQueue wbuf;

	FlexIDSocket socket;
	private Thread inThread;
	private Thread outThread;
	
	public FlexIDSession(FlexID sFID, FlexID dFID, FlexIDSocket sock) {
		SFID = sFID;
		DFID = dFID;

		try {
			MessageDigest sh = MessageDigest.getInstance("SHA-256");
			sh.update(sFID.getIdentity());
			sh.update(dFID.getIdentity());
			connID = sh.digest();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		sentSEQ = 0;
		sentACK = 0;
		
		rbuf = new CircularQueue();
		wbuf = new CircularQueue();
		if(sock != null)
			socket = sock;
		else
			socket = new FlexIDSocket(DFID);
		
		inThread = new Thread(new inbound());
		inThread.setDaemon(true);
		inThread.start();
		
		outThread = new Thread(new outbound());
		outThread.setDaemon(true);
		outThread.start();
	}
	public FlexIDSession(FlexID sFID, FlexID dFID) {
		this(sFID, dFID, null);
	}
	public static void mobility() {
		port = 3336; // change port.
		accept();	
	}
	public static FlexIDSession accept() {
		FlexIDServerSocket server = new FlexIDServerSocket(port);
		System.out.println("Server waits a connections.");
		FlexIDSocket sock = server.accept();
		if(sock == null) {
			System.out.println("accept failed.");
			return null;
		}
		
		System.out.println("Connected.");
		FlexID sFID = new FlexID();
		FlexID dFID = new FlexID();
		
		return new FlexIDSession(sFID, dFID, sock);
	}

	// Application send
	public int send(byte[] msg) {
		int mLen;

		if((mLen = wbuf.write(msg)) < 0) {
			System.out.println("mLen: " + mLen);
			lock = 0;
			return -1;
		}
		else {
			lock = 0;
			return mLen;
		}
	}
	// Application receive
	public int receive(byte[] b) {
		int bLen = rbuf.read(b);
		
		if(b == null) {
			lock = 0;
			return -1;
		}
		else {
			lock = 0;
			return bLen; 
		}
	}
	
	// Check wbuf to send msg to socket.
	public int checkMsgToSend() {
		if(wbuf.isEmpty(1)) {
			return -1;
		}
		else return 1;
	}
	
	// Polling: To get msg from socket. Then write to the rbuf.
	public byte[] getRecvMsg() {
		try {
			byte[] message = socket.read(); // is it block?
			
			if(message != null) return message;
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		
		return null;
	}
	
	private class inbound implements Runnable {
		public void run() {
			try {
				while(!inThread.isInterrupted()) {
					// checkAddress();	// TODO
					byte[] message;
					if(lock != 1) {
						lock = 1;
					
						if((message = getRecvMsg()) != null) {
							
							byte[] header = getHeader(message); // length(2B) + connID(20B) + seq(4B), ack(4B)
							byte[] data = getData(message);
							byte[] msgConnID = Arrays.copyOfRange(header, 2, 22);
							
							if(!Arrays.equals(connID, msgConnID)) {
								System.out.println("connID unmatched.");
								lock = 0;
								continue;
							}
							
							int length = Conversion.byte2ToInt(Arrays.copyOfRange(header, 0, 2)); // total length
							int msgSeq = Conversion.byte4ToInt(Arrays.copyOfRange(header, 22, 26));
							int msgAck = Conversion.byte4ToInt(Arrays.copyOfRange(header, 26, 30));
							
							if(length > 30) { // received data message.
								if(sentACK < msgSeq) {
									recvSEQ = msgSeq;
									sentACK = msgSeq+1;
									rbuf.write(data);
								}
									
								// send ACK message.
								byte[] ACKmessage = setHeader(null);
//								System.out.println("Wait for sending ACK (2s)");
//								Thread.sleep(2000);
								System.out.println("Send ACK message #: " + sentACK);
								
//								Conversion.byteToAscii(ACKmessage);
								socket.write(ACKmessage);
//								System.out.println("ACK write done.");
							}
							else { // received ACK message.
								System.out.println("Received ACK message");
								if(msgAck >= (sentSEQ+1)) {
									recvACK = msgAck;
								}
							}
						}
						lock = 0;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			} finally {
				lock = 0;
			}

			System.out.println("inThread done.");
		}
	}
		
	private class outbound implements Runnable {
		public void run() {
			try {
				byte[] message = new byte[30];
				boolean retransmission = false;
				while(!outThread.isInterrupted()) {					
					if(lock != 1) {
						lock = 1;
											
						//checkAddress();	// TODO
						/* if ip changed, creates new FlexIDSocket, connects,
						   and retransmits the last message if necessary(stop-and-wait)   */ 
						if(retransmission == true) {
							socket.write(message); // stop-and-wait
							lock = 0;
							continue;
						}
						if((recvACK != (sentSEQ+1)) && (sentSEQ != 0)) {
							lock = 0;
							continue;
						}
						
						if(checkMsgToSend() == 1) {
							lock = 1;
							System.out.println("send wbuf msg to socket");
							byte[] data = new byte[2048];
							int dataLen = wbuf.read(data);
							data = Arrays.copyOfRange(data, 0, dataLen);
							
							byte[] header = setHeader(data);
							message = new byte[30 + data.length];
							
							System.arraycopy(header, 0, message, 0, 30);
							System.arraycopy(data, 0, message, 30, data.length);
	//						System.out.println("MSG: ");
	//						Conversion.byteToAscii(message);
							socket.write(message);
						}
						
						lock = 0;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			} finally {
				lock = 0;
			}
			
			System.out.println("outThread done.");
		}
	}
	
	public byte[] getHeader(byte[] message) {
		return Arrays.copyOfRange(message, 0, 30);
	}
	public byte[] getData(byte[] message) {
		int dataLength = Conversion.byte2ToInt(Arrays.copyOfRange(message, 0, 2)) - 30;
		byte[] data = new byte[dataLength];
		System.arraycopy(message, 30, data, 0, dataLength);
		return data;
	}
	public byte[] setHeader(byte[] message) {
		try {
			// Header(30) = length(2B) + connID(20B) + seq(4B), ack(4B)
			byte[] length = new byte[2];
			byte[] seq = new byte[4];
			byte[] ack = new byte[4];
			byte[] header = new byte[30];
			
			int msgLength;
			
			if(message == null) { // ack 
				msgLength = 30;
//				System.out.println("ACK: " + sentACK);
				ack = Conversion.int32ToByteArray(sentACK);
				
			}
			else { // data
				msgLength = message.length + 30;
				sentSEQ += (msgLength - 30);
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
	
	public void close() {
		socket.close();
	}
	public FlexID getSFID() {
		return SFID;
	}
	public void setSFID(FlexID sFID) {
		SFID = sFID;
	}
	public FlexID getDFID() {
		return DFID;
	}
	public void setDFID(FlexID dFID) {
		DFID = dFID;
	}
}


