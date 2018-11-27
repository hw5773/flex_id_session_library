package versatile.flexidsession;

import java.nio.ByteBuffer;

public class Conversion {
	public static int byte4ToInt(byte[] b) {
		return (b[3] & 0xFF) |
				((b[2] & 0xFF) << 8) |
				((b[1] & 0xFF) << 16) |
				((b[0] & 0xFF) << 24);
	}
	public static int byte2ToInt(byte[] b) {
	    return  (b[1] & 0xFF) |
	            (b[0] & 0xFF) << 8;
	}
	
	public static byte[] int16ToByteArray(int a) {
		return new byte[] {
				(byte) ((a >> 8) & 0xFF),
				(byte) (a & 0xFF)
		};
	}
	public static byte[] int32ToByteArray(int a) {
		return new byte[] {
				(byte) ((a >> 24) & 0xFF),
				(byte) ((a >> 16) & 0xFF),
				(byte) ((a >> 8) & 0xFF),
				(byte) (a & 0xFF)
		};
	}
	
	synchronized public static void byteToAscii(byte[] b) {
		System.out.print("[Ascii]	");
		for(int i=0; i<b.length; i++) {
			System.out.print((int)b[i] + " ");
			if((i+1) % 10 == 0) {
				System.out.println();
				System.out.print("	");
			}
		}
		System.out.println();
	}
	public static void byteToAscii(byte[] b, int n) {
		System.out.print("[Ascii]	");
		for(int i=0; i<n; i++) {
			System.out.print((int)b[i] + " ");
			if((i+1) % 10 == 0) {
				System.out.println();
				System.out.print("	");
			}
		}
		System.out.println();
	}
	public static void printString(byte[] b) {
		System.out.print("Text [String format]: ");
		String str = new String(b);
		System.out.println(str);
	}

}
