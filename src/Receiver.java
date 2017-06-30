
package networkproject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver
{	
	//set invariant variables
	final static int SENT_MSG_SIZE = 8;
	final static int DATA_SIZE = 500;
	final static int MAX_PAC_SIZE = 30;
	final static int TRANS_PORT_NUM = 7777;
	final static int PACKETTYPE_LOC = 0;
	final static int ACKNUM_LOC = 2;
	final static int CHCKSUM_LOC = 4;
	final static int RECBUFSIZE_LOC = 6;
	final static int DATA_LOC = 8;
	
	
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
	

    public static int byteToInt(byte[] b) //byte[] to int
    {
        int s = 0;
        int s0 = b[1] & 0xff;
        int s1 = b[0] & 0xff;
        s1 <<= 8;
        s = s0 | s1;
        return s;
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

    
    public static byte[] setSMSG(int packectType, int ackNum, int recBufSize)//set sentmessage
    {
    	byte[] sentMessage= new byte[SENT_MSG_SIZE];
    	byte[] packetTypeAr = new byte[2];
		byte[] ackNumAr = new byte[2];
		byte[] checksumAr = new byte[2];
		byte[] recBufSizeAr = new byte[2];
		//set info to the sent message
		packetTypeAr = intToByte(packectType);
		ackNumAr = intToByte(ackNum);
		checksumAr = intToByte(0);
		recBufSizeAr = intToByte(recBufSize);
		sentMessage[PACKETTYPE_LOC] = packetTypeAr[0];
		sentMessage[PACKETTYPE_LOC+1] = packetTypeAr[1];
		sentMessage[ACKNUM_LOC] = ackNumAr[0];
		sentMessage[ACKNUM_LOC+1] = ackNumAr[1];
		sentMessage[RECBUFSIZE_LOC] = recBufSizeAr[0];
		sentMessage[RECBUFSIZE_LOC+1] = recBufSizeAr[1];
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
		
		byte[] sentMessage = new byte[SENT_MSG_SIZE];
		byte[] data = new byte[DATA_SIZE];
		//open receiver socket
		InetAddress trans = InetAddress.getLocalHost();
		DatagramSocket receiverSocket = new DatagramSocket(TRANS_PORT_NUM, trans);
		InetAddress transAddress;
		System.out.println("Start receiving:");
		//initial variables
		int preLength = MAX_PAC_SIZE;
		boolean key = true;
		int ackNum = 0;	
		//main loop
		while(key)
		{
			//receive packet
			byte[] receivedMessage = new byte[MAX_PAC_SIZE];
			DatagramPacket receivePacket = new DatagramPacket(receivedMessage, receivedMessage.length);//只需要信息跟长度	
			receiverSocket.receive(receivePacket);//此方法在收到数据前会一直阻塞
			//get info from packet
			transAddress = receivePacket.getAddress();
			int transPort = receivePacket.getPort();
			int dataLength = receivePacket.getLength();
			byte[] recMSG = new byte[dataLength];
			for(int ind = 0; ind < dataLength; ind++)
			{
				recMSG[ind] = receivePacket.getData()[ind];
			}
			//judge packet type, data length and checksum for continuation packet
			if(recMSG[PACKETTYPE_LOC+1]==0&&dataLength<=preLength&&(checkSum(recMSG)<<16)==0)
			{
				// record the data from payload into data[]
				for(int ind = DATA_LOC; ind <dataLength; ind++)
				{
					data[ackNum+ind-DATA_LOC]=recMSG[ind];
				}
				//generate a even number between MPS/2 and MPS (doing this is to ensure the packet length keep even easily)
				int recBufSize= (int) (2*(int)(Math.random()*8+8));
				//get acknowledge number
				ackNum = ackNum+dataLength-DATA_LOC;
				//set sent packet
				sentMessage = setSMSG(2, ackNum, recBufSize);
				DatagramPacket sentPacket = new DatagramPacket(sentMessage, sentMessage.length);				
				sentPacket.setAddress(transAddress); 
			    sentPacket.setPort(transPort);  	
				receiverSocket.send(sentPacket);
				//record this recBufSize for comparing with data length of next packet
				preLength= recBufSize;
			}
			//judge packet type, data length and checksum for last packet
			if(recMSG[PACKETTYPE_LOC+1]==1&&dataLength<=preLength&&(checkSum(recMSG)<<16)==0)
			{
				// record the data from payload into data[]
				for(int ind = DATA_LOC; ind <dataLength; ind++)
				{
					data[ackNum+ind-DATA_LOC]=receivePacket.getData()[ind];
				}
				//print out all data
				for(int i = 0; i < data.length/20; i++)
				{
					System.out.printf("Data number %3d - %3d: ",i*20+1, i*20+20);
					for(int j = 0; j < 20; j++)
					{
						System.out.printf("%5d", data[i*20+j]);
					}
					System.out.println();				
				}
				//finish working
				System.out.println("Data receiving accomplish!");
				key = false;
			}
		}//while
		//close receiver socket
		receiverSocket.close();
	} //main
} //class