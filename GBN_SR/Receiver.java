import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.*;
import java.io.*;


public class Receiver {

	// Maximum Segment Size - Size of data
	public static  int MSS;

	// Probability of loss during sending
	public static  double PROBABILITY = 0.1;

	// Window size - Number of packets sent without acking
	public static  int WindowSize;

	// Time (ms) before REsending all the non-acked packets
	public static  int TIMER;

	public static String[] inpfile=new String[5];

	public static int inpfile(String filename){
		int k=0;
		Scanner data = null;
		try {
			data = new Scanner(new File(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();  
		}
		while (data.hasNextLine()) {
			Scanner data2 = new Scanner(data.nextLine());
			while (data2.hasNext()) {
				String str = data2.next();
				inpfile[k] = str;
				k = k+1;
				System.out.println(str);
			}
		}
		MSS = Integer.parseInt(inpfile[4]);
		PROBABILITY = 0.1;
		WindowSize = Integer.parseInt(inpfile[2]);
		TIMER = Integer.parseInt(inpfile[3]);

		return Integer.parseInt(inpfile[4]);
	}

	public static boolean useLoop(int[] arr, int t_Val) {
		for(int str: arr) {
			if(str==t_Val)
				return true;
		}
		return false;
	}

	public static void main(String[] args) throws Exception {

		String fileName= "", portStringNum = "" ; //Taking arguments from the client to connect to the server.

		if (args.length < 2 || args.length > 2) {
			System.out.println("Invalid Entry by user. Valid Entry: java Receiver <Input_File_Name> <port_number>");
		} else {       //If the arguments are correct and in proper order then store them in proper variables. 
			fileName = args[0];
			portStringNum = args[1];//Integer.parseInt(args[1]);
		}
		int port = Integer.parseInt(portStringNum);
		inpfile(fileName);

		DatagramSocket fromSender = new DatagramSocket(port);

		if(inpfile[0].equalsIgnoreCase("gbn")){
			byte[] receivedData = new byte[MSS + 93];
			int waitingFor = 0;
			String receivedMsg;
			int rec_chk = 0;
			boolean last = false;
			ArrayList<RDTPacket> received = new ArrayList<RDTPacket>();
			boolean end = false;

			while(!end) {

				System.out.println("\nWaiting for packet"+waitingFor);

				// Receive packet
				DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);
				fromSender.receive(receivedPacket);

				// Unserialize to a RDTPacket object
				RDTPacket packet = (RDTPacket) Serializer.toObject(receivedPacket.getData());
				receivedMsg="";

				for(byte b: packet.getData()){
					receivedMsg=receivedMsg+(char) b;
				}
				rec_chk=ChecksumMethod.generateChecksum(receivedMsg);

				System.out.println("Packet Number " + packet.getSeq() + " Received (last: " + packet.isLast() + " )");

				if(packet.chk != rec_chk) {
					System.out.println("Packet Discarded (Checksum Error)");
				} else if (packet.getSeq() == waitingFor && packet.isLast()) {

					waitingFor++;
					received.add(packet);
					last=true;
					System.out.println("Last Packet Received");

					end = true;

				} else if(packet.getSeq() == waitingFor) {
					waitingFor++;
					received.add(packet);
					System.out.println("Packed Number "+(waitingFor-1)+" stored in buffer");
				} else {
					System.out.println("Packet Discarded (not in order)");
				}
				// Create an RDTAck object
				RDTAck ackObject = new RDTAck(waitingFor,last);

				// Serialize
				byte[] ackBytes = Serializer.toBytes(ackObject);

				DatagramPacket ackPacket = new DatagramPacket(ackBytes, ackBytes.length, receivedPacket.getAddress(), receivedPacket.getPort());

				// Send with some probability of loss
				if(Math.random() > PROBABILITY) {
					fromSender.send(ackPacket);
				} else {
					System.out.println("[X] ACK Lost with Sequence Number " + ackObject.getPacket());
				}

				System.out.println("Sending ACK to Sequence " + waitingFor + " with " + ackBytes.length  + " bytes");

			}

		} else if (inpfile[0].equalsIgnoreCase("sr")) {
			byte[] receivedData = new byte[Sender.inpfile(fileName) + 93];
			int[] waitAck = new int[WindowSize];
			for (int k=0;k<WindowSize;k++)
				waitAck[k]=k;

			int sendACK=0,lastPack=-1;

			int[] setACK = new int[WindowSize];

			int shift=0;
			String rec_message;
			int chk_rec=0;
			boolean last=false;
			ArrayList<RDTPacket> received = new ArrayList<RDTPacket>();
			InetAddress Address = InetAddress.getByName("localhost");
			boolean end = false;

			while(!end) {

				// Receive packet
				DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);
				fromSender.receive(receivedPacket);

				// Unserialize to a RDTPacket object
				RDTPacket packet = (RDTPacket) Serializer.toObject(receivedPacket.getData());
				rec_message="";
				for(byte b: packet.getData()) {
					rec_message=rec_message+(char) b;
				}
				chk_rec=ChecksumMethod.generateChecksum(rec_message);

				System.out.println("Packet Number " + packet.getSeq() + " Received (last: " + packet.isLast() + " )");

				if(packet.chk != chk_rec) {
					System.out.println("\n[X] CHECKSUM ERROR --(Packet Discarded)--");
				} else if(useLoop(waitAck,packet.getSeq()) && packet.isLast()) {

					received.add(packet);
					last=true;
					lastPack=packet.getSeq();
					System.out.println("\nLast packet received");
					for(int m=0;m<waitAck.length;m++)
						if(packet.getSeq()==waitAck[m]) {
							setACK[m]=packet.getSeq();break;
						}
				} else if( useLoop(waitAck,packet.getSeq()) ) {

					received.add(packet);
					sendACK=packet.getSeq();
					System.out.println("\nGENERATED ACK  "+sendACK);
					System.out.println("Packet stored in buffer");
					for(int m=0;m<waitAck.length;m++)
						if(packet.getSeq()==waitAck[m]) {
							setACK[m]=1;break;
						}
				} else {
					System.out.println("\nPacket discarded (NOT IN ORDER)");
				}

				// Create an RDTAck object
				RDTAck ackObject = new RDTAck(sendACK,last);

				// Serialize
				byte[] ackBytes = Serializer.toBytes(ackObject);

				DatagramPacket ackPacket = new DatagramPacket(ackBytes, ackBytes.length, receivedPacket.getAddress(), receivedPacket.getPort());
				Address=receivedPacket.getAddress();
				port=receivedPacket.getPort();

				// Send with some probability of loss
				if(Math.random() > PROBABILITY){
					fromSender.send(ackPacket);
					for(int m=0;m<waitAck.length;m++)
						if(ackObject.getPacket()==waitAck[m]){
							setACK[m]=1;break;
						}
					System.out.println("Sending ACK to Sequence " + sendACK);
				} else {
					System.out.println("\n[X] Lost ACK with Sequence Number " + ackObject.getPacket());
					for(int m=0;m<waitAck.length;m++)
						if(ackObject.getPacket()==waitAck[m]){
							setACK[m]=0;break;
						}
				}

				for(int n=0;n<WindowSize;n++){
					if(setACK[n]==1){

						shift++;
					}else
						break;
				}
				if (shift!=0){
					for(int n=0;n<WindowSize-shift;n++){
						setACK[n]=setACK[n+shift];
						waitAck[n]=waitAck[n+shift];
					}
					for(int n=WindowSize-shift;n<WindowSize;n++){
						setACK[n]=0;
						if(n==0){
							waitAck[n]=waitAck[waitAck.length-1]+1;
							n=n+1;
							setACK[n]=0;
						}
						waitAck[n]=waitAck[n-1]+1;
					}
					shift=0;
				}

				System.out.println("Waiting for: ");
				for(int n=0;n<WindowSize;n++)
					System.out.println(waitAck[n]);

				System.out.println("setACK: ");
				for(int n=0;n<WindowSize;n++)
					System.out.println(setACK[n]);
				end=true;
				for(int n=0;n<setACK.length;n++){
					if(n==0 && setACK[n]==lastPack){
						break;
					}else if(setACK[n]==lastPack){
						for(int m=0;m<n;m++)
							if(setACK[m]==0){
								end=false;
								break;
							}
					}else end=false;
				}

				if(end==true){
					// Create an RDTAck object
					ackObject = new RDTAck(lastPack+1,true);

					// Serialize
					ackBytes = Serializer.toBytes(ackObject);


					ackPacket = new DatagramPacket(ackBytes, ackBytes.length, Address, port);

					fromSender.send(ackPacket);
				}
			}

		}


	}


}
