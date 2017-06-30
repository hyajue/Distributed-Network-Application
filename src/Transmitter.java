
package networkproject;

import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;


public class Transmitter
{
	//set invariant variables
	final static int DATA_SIZE = 500;
	final static int REC_MSG_SIZE = 8;
	final static int TRANS_PORT_NUM = 7777;
	final static int PACKETTYPE_LOC = 0;
	final static int SEQNUM_LOC = 2;
	final static int CHCKSUM_LOC = 4;
	final static int LENGTH_LOC = 6;
	final static int DATA_LOC = 8;
	final static int ACKNUM_LOC = 2;
	final static int RECBUFSIZE_LOC = 6;
	
	
	public static byte[] intToByte(int number) 
	//int to byte[] (actually this method is for short to byte[], but we don't have large int in this project, so this is ok to work)
	{
	    int temp = number;
	    byte[] a = new byte[2];
	    for (int i = a.length-1; i >= 0; i--) 
	    {
	        a[i] = new Integer(temp & 0xff).byteValue();
	        temp = temp >> 8; 
	    }
	    return a;
	}

	
    public static int byteToInt(byte[] b)//byte[] to int
    {
        int s = 0;
        int s0 = b[1] & 0xff;
        int s1 = b[0] & 0xff;
        s1 <<= 8;
        s = s0 | s1;
        return s;
    }
   
    
    public static byte[] tempData(int seqNum, int bufSize, byte[] data)//get the required data form the total data[]
    {
		byte[] tempData = new byte[bufSize-DATA_LOC];
		for(int i=seqNum;i<bufSize-DATA_LOC+seqNum;i++)
		{
			tempData[i-seqNum] = data[i];
		}
		return tempData;
    }
 
    
    public static int checkSum(byte[] c)// checksum calculating (using hw6 method. We didn't figure out the better ones.)
    {
    	long sum = 0;
		for(int ind = 0; ind < c.length/2; ind++)
		{
			byte msbByte = c[2*ind];
			byte lsbByte = c[2*ind+1];
			int msb = msbByte >= 0 ? msbByte : msbByte + 256;
			int lsb = lsbByte >= 0 ? lsbByte : lsbByte + 256;
			sum += (msb << 8) + lsb;
			while(sum >> 16 != 0)
				sum = (sum >> 16) + (sum & 0xffff);
		}
		return (int)~sum;
    }
 
    
    public static byte[] setSMSG(int packectType, int seqNum, int length, int recBufSize, byte[] tempData)//set sentmessage
    {
    	byte[] sentMessage = new byte[recBufSize];
    	byte[] packetTypeAr = new byte[2];
		byte[] seqNumAr = new byte[2];
		byte[] checksumAr = new byte[2];
		byte[] lengthAr = new byte[2];
		//set info to the sent message
		packetTypeAr = intToByte(packectType);
		seqNumAr = intToByte(seqNum);
		checksumAr = intToByte(0);
		lengthAr = intToByte(length);
		sentMessage[PACKETTYPE_LOC] = packetTypeAr[0];
		sentMessage[PACKETTYPE_LOC+1] = packetTypeAr[1];
		sentMessage[SEQNUM_LOC] = seqNumAr[0];
		sentMessage[SEQNUM_LOC+1] = seqNumAr[1];
		sentMessage[LENGTH_LOC] = lengthAr[0];
		sentMessage[LENGTH_LOC+1] = lengthAr[1];
		for(int i=0;i<recBufSize-DATA_LOC;i++)
		{
			sentMessage[i+DATA_LOC]=tempData[i];
		}		
		//check message length to keep it be even
		if(sentMessage.length % 2 == 0)
		{
			int checkSum = checkSum(sentMessage);
			checksumAr = intToByte(checkSum);
			sentMessage[CHCKSUM_LOC] = checksumAr[0];
			sentMessage[CHCKSUM_LOC+1] = checksumAr[1];
		}
		else
		{
			System.out.println("massage length error!");
		}
		return sentMessage;
    }
 
    
	public static void main(String[] args) throws Exception
	{	
		//generate 500 bytes random number
		Random randData = new Random();
		byte[] data = new byte[DATA_SIZE];
		randData.nextBytes(data);
		//open receiver socket
		InetAddress receiver = InetAddress.getLocalHost();//同一个电脑
		DatagramSocket transSocket = new DatagramSocket();//实现数据发送	
		//sent first message
		int firstBufSize = 30;
		byte[] firstSentMessage = setSMSG(0, 0, firstBufSize-DATA_LOC, firstBufSize, tempData(0,firstBufSize,data));
		DatagramPacket firstSentPacket = new DatagramPacket(firstSentMessage, firstSentMessage.length, receiver, TRANS_PORT_NUM);//发送数据，长度，receiver地址，port
		transSocket.send(firstSentPacket);//发送
		
		System.out.println("Start transmission! If it is time out, please try to press 'RUN' button again after stop transmission!");
		//initial sequence number
		int seqNum = 0;
		//main loop
		while(seqNum < DATA_SIZE)
		{
			//set received packet
			byte[] receivedMessage = new byte[REC_MSG_SIZE];
			DatagramPacket receivedPacket = new DatagramPacket(receivedMessage,receivedMessage.length);
			//timer for receiving packet
			for(int i = 1; i<=4; i++)
			{	
				try
				{				
					transSocket.setSoTimeout(1000*i);
					transSocket.receive(receivedPacket);
					break;
				}
				catch(InterruptedIOException e)
				{
					if(i <= 4)
					{
						System.out.printf("Time out times: %d. \n", i);	
						if(i==4)
						{
							System.out.println("Stop transmission!");
							System.exit(0);
						}
					}						
				}			
			}
			//get info from received packet
			byte[] recMSG = new byte[REC_MSG_SIZE];
			for(int ind = 0; ind < REC_MSG_SIZE; ind++)
			{
				recMSG[ind] = receivedPacket.getData()[ind];
			}
			//judge packet type and checksum
			if(recMSG[PACKETTYPE_LOC+1]==2&&(checkSum(recMSG)<<16)==0)
			{
				//get acknowledge number and receive buffer size from received packet
				byte[] ackNumAr = {receivedPacket.getData()[ACKNUM_LOC], receivedPacket.getData()[ACKNUM_LOC+1]};
				byte[] recBufSizeAr = {receivedPacket.getData()[RECBUFSIZE_LOC], receivedPacket.getData()[RECBUFSIZE_LOC+1]};
				int ackNum = byteToInt(ackNumAr);
				int recBufSize = byteToInt(recBufSizeAr);
				//continue to sent data
				if(recBufSize+ackNum-DATA_LOC < DATA_SIZE)
				{
					byte[] sentMessage = setSMSG(0, ackNum, recBufSize-DATA_LOC, recBufSize, tempData(ackNum,recBufSize,data));
					DatagramPacket sentPacket = new DatagramPacket(sentMessage, sentMessage.length, receiver, TRANS_PORT_NUM);
					transSocket.send(sentPacket);
					//update sequence number
					seqNum = recBufSize+ackNum-DATA_LOC;
				}
				//last packet of data and stop whole progress
				else
				{
					int lastSize = DATA_SIZE-ackNum+DATA_LOC;
					byte[] sentMessage = setSMSG(1, ackNum, lastSize-DATA_LOC, lastSize, tempData(ackNum,lastSize,data));
					DatagramPacket sentPacket = new DatagramPacket(sentMessage, sentMessage.length, receiver, TRANS_PORT_NUM);
					transSocket.send(sentPacket);
					//update sequence number to end the main loop
					seqNum = DATA_SIZE;
					//transmission accomplish, and close transmitter socket.
					System.out.println("Data transmission accomplish! ");
					transSocket.close();
					System.exit(0);
				}
						
			}
			else
				System.out.println("Data error!");

		}//while
	}//main()
} //class