package versatile.flexidsession;

import java.util.Arrays;

public class CircularQueue {
	private int currentSize;
	private int front;
	private int rear;
	private int maxSize;
	private byte[] queueArray;
	
	public CircularQueue(int maxSize) {
		this.currentSize = 0;
		this.front = -1;
		this.rear = -1;
		
		this.maxSize = maxSize;
		this.queueArray = new byte[this.maxSize];
	}
	
	public CircularQueue() {
		this(10000000);
	}
	
	public boolean isEmpty(int size) {
		return (currentSize - size < 0);
	}
	
	public boolean isFull(int size) {
		return (currentSize + size > this.maxSize);
	}
	
	synchronized public int write(byte[] item) {
		if(isFull(item.length)) return -1;
		else {
			int readFrom = 0;
			int readLen = item.length;
			
			rear = (rear + 1) % this.maxSize;
			if((rear+item.length) >= this.maxSize) {
				System.arraycopy(item, 0, queueArray, rear, (this.maxSize-rear));
				
				readFrom = this.maxSize-rear;
				readLen = item.length - (this.maxSize-rear);
				rear = 0;
			}
						
			System.arraycopy(item, readFrom, queueArray, rear, readLen);
			rear = (rear + readLen - 1) % this.maxSize;
			currentSize += item.length;
			if(front == -1) {
				front = 0;
			}
			return item.length;
		}

	}
		
	synchronized public int read(byte[] b) {
		int size = b.length; // size: amount of read
		
		if(currentSize < size) {
			size = currentSize;
		}
		
		
		
		if(currentSize == 0) {
			return -1;
		}
		else {
			int writeFrom = 0;
			int writeLen = size;
			
			if((front+size) > this.maxSize) {
				System.arraycopy(queueArray, front, b, 0, (this.maxSize-front));
				
				writeFrom = this.maxSize-front;
				writeLen = size - (this.maxSize-front);
				Arrays.fill(queueArray,  front, front+(this.maxSize-front), (byte) 0);
				front = 0;
			}
			System.arraycopy(queueArray, front, b, writeFrom, writeLen);

			Arrays.fill(queueArray, front, front+size, (byte) 0);
			front = (front + writeLen) % this.maxSize;
			
			currentSize -= size;
			
			return size;
		}
	}
}
