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

public class Sender {

	// Maximum Segment Size - Size of data
	public static  int MSS;

	// Probability of loss during sending
	public static  double PROBABILITY = 0.1;

	// Window size - Number of packets sent without acking
	public static  int WSIZE;

	// Time (ms) before Resending all the non-acked packets
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
				inpfile[k]=str;
				k=k+1;
				System.out.println(str);
			}
		}
		MSS = Integer.parseInt(inpfile[4]);
		PROBABILITY = 0.1;
		WSIZE = Integer.parseInt(inpfile[2]);
		TIMER = Integer.parseInt(inpfile[3]);
		return Integer.parseInt(inpfile[4]);
	}

	public static void main(String[] args) throws Exception {

		String fileName= "", portNum = "", lstSq1 = "" ; //Taking arguments from the client to connect to the server.

		if (args.length < 3 || args.length > 3) {
			System.out.println("Invalid Entry by user. Valid Entry: java Sender <Input_File_Name> <port_number> <Number_of_Packets>");
		} 
		else            //If the arguments are correct and in proper order then store them in proper variables. 
		{
			fileName = args[0];
			portNum = args[1];
			lstSq1 = args[2];
		}
		int port= Integer.parseInt(portNum);
		int lastSq= Integer.parseInt(lstSq1);

		inpfile(fileName);

		if(inpfile[0].equalsIgnoreCase("gbn")){

			int chk_sum=0,pkt_cnt=0;

			// Sequence number of the last packet sent (Receiver base)
			int lastPacketSent = 0;

			// Sequence number of the last acked packet
			int w8Ack = 0;

			// Data to be sent
			byte[] data_Bytes = new byte[MSS];
			System.out.println("Data size: " + data_Bytes.length + " bytes");

			int chk_Count = 0;
			boolean chk_Flag = false;

			System.out.println("Number of packets to send: " + lastSq);

			DatagramSocket toReceiver = new DatagramSocket();

			// Receiver address
			InetAddress receiverAddress = InetAddress.getByName("localhost");

			// List of all the packets sent
			ArrayList<RDTPacket> sent = new ArrayList<RDTPacket>();

			while(true){

				// Sending loop
				while(lastPacketSent - w8Ack < WSIZE && lastPacketSent < lastSq){

					String data_Msg="CCN PROJECT 2: Go-Back-N Protocol";
					byte[] filePacketBytes = new byte[MSS];
					filePacketBytes = data_Msg.getBytes();
					chk_sum=ChecksumMethod.generateChecksum(data_Msg);
					System.out.println("Checksum = "+ chk_sum);

					if(Math.random() > PROBABILITY){
					}else{
						chk_sum=chk_sum+2;
						chk_Flag=true;
						System.out.println("\n[X] Checksum changed for packet number " + lastPacketSent);
					}
					// Create RDTPacket object
					RDTPacket rdtPacketObject = new RDTPacket(chk_sum,lastPacketSent, filePacketBytes, (lastPacketSent == lastSq-1) ? true : false);

					// Serialize the RDTPacket object
					byte[] data_Sending = Serializer.toBytes(rdtPacketObject);

					// Create the packet
					DatagramPacket packet = new DatagramPacket(data_Sending, data_Sending.length, receiverAddress, port );

					System.out.println("\nSending packet with number " + lastPacketSent);

					// Add packet to the sent list
					if(chk_Flag==true){
						// Create RDTPacket object
						RDTPacket rdtPacketObject_ce = new RDTPacket(chk_sum-2,lastPacketSent, filePacketBytes, (lastPacketSent == lastSq-1) ? true : false);

						// Serialize the RDTPacket object
						byte[] data_Sending_ce = Serializer.toBytes(rdtPacketObject);

						// Create the packet
						DatagramPacket packet_ce = new DatagramPacket(data_Sending, data_Sending.length, receiverAddress, port );
						sent.add(rdtPacketObject_ce);
						chk_Flag=false;	
					}else sent.add(rdtPacketObject);

					chk_Count++;
					if(Math.random() > PROBABILITY){
						toReceiver.send(packet);
						System.out.println("Timer Started");
					}else{
						System.out.println("[X] Lost packet with number " + lastPacketSent);
					}

					// Increase the last sent
					lastPacketSent++;
				} // End of sending while

				// Byte array for the ACK sent by the receiver
				byte[] ackBytes = new byte[48];

				// Creating packet for the ACK
				DatagramPacket ack = new DatagramPacket(ackBytes, ackBytes.length);

				try{
					// If an ACK was not received in the time specified (continues on the catch clausule)
					toReceiver.setSoTimeout(TIMER);
					toReceiver.receive(ack);

					// Unserialize the RDTAck object
					RDTAck ackObject = (RDTAck) Serializer.toObject(ack.getData());

					System.out.println("Received ACK for " + ackObject.getPacket());

					// If this ack is for the last packet, stop the sender (Note: gbn has a cumulative acking)
					if(ackObject.getPacket() == lastSq){
						break;
					}

					w8Ack = Math.max(w8Ack, ackObject.getPacket());

				}catch(SocketTimeoutException e){
					// then send all the sent but non-acked packets
					System.out.println("Timer Expired");/////////////////////////////////////////
					for(int i = w8Ack; i < lastPacketSent; i++){

						// Serialize the RDTPacket object
						byte[] data_Sending = Serializer.toBytes(sent.get(i));

						// Create the packet
						DatagramPacket packet = new DatagramPacket(data_Sending, data_Sending.length, receiverAddress, port );

						// Send with some probability
						if(Math.random() > PROBABILITY){
							toReceiver.send(packet);
						}else{
							System.out.println("\n[X] Lost packet with number " + sent.get(i).getSeq());
						}

						System.out.println("\nResending packet with number " + sent.get(i).getSeq());
						System.out.println("Timer Restarted");
					}
				}
			}

			System.out.println("\n\tFinished Transmission!!! :)");

		} else if(inpfile[0].equalsIgnoreCase("sr")) {
			int chk=0,packet_count=0;

			// Sequence number of the last packet sent (rcvbase)
			int lastPacketSent = 0;

			// Sequence number of the last acked packet
			int w8Ack = 0;

			// Data to be sent
			byte[] data_Bytes = new byte[MSS];
			System.out.println("Data size: " + data_Bytes.length + " bytes");

			int chk_count = 0;
			int sBase=0;
//			int ack_Cout=0;
			boolean chk_flag=false;

			System.out.println("Number of packets to send: " + lastSq);

			DatagramSocket toReceiver = new DatagramSocket();

			// Receiver address
			InetAddress receiverAddress = InetAddress.getByName("localhost");

			// List of all the packets sent
			ArrayList<RDTPacket> sent = new ArrayList<RDTPacket>();

			int[] acks = new int[lastSq];
			while(true){

				// Sending loop
				while(lastPacketSent < sBase+WSIZE && lastPacketSent < lastSq){
					String msg="CCN	PROJECT 2: Selective Repeat Protocol";
					byte[] filePacketBytes = new byte[MSS];
					filePacketBytes = msg.getBytes();
					chk = ChecksumMethod.generateChecksum(msg);
					System.out.println("Checksum Generated is = "+ chk);

					if(chk_count==(int)(0.1*lastSq+2)){
						chk=chk+2;
						chk_count=0;
						chk_flag=true;
						System.out.println("\nCHECKSUM CHANGED");
					}

					// Create RDTPacket object
					RDTPacket rdtPacketObject = new RDTPacket(chk,lastPacketSent, filePacketBytes, (lastPacketSent == lastSq-1) ? true : false);

					// Serialize the RDTPacket object
					byte[] data_Sending = Serializer.toBytes(rdtPacketObject);

					// Create the packet
					DatagramPacket packet = new DatagramPacket(data_Sending, data_Sending.length, receiverAddress, port );

					// Add packet to the sent list
					if(chk_flag==true){
						// Create RDTPacket object
						RDTPacket rdtPacketObject_ce = new RDTPacket(chk-2,lastPacketSent, filePacketBytes, (lastPacketSent == lastSq-1) ? true : false);

						// Serialize the RDTPacket object
						byte[] data_Sending_ce = Serializer.toBytes(rdtPacketObject);

						// Create the packet
						DatagramPacket packet_ce = new DatagramPacket(data_Sending, data_Sending.length, receiverAddress, port );
						sent.add(rdtPacketObject_ce);
						chk_flag=false;	
					}else sent.add(rdtPacketObject);

					// Send with some probability of loss
					chk_count++;
					if(Math.random() > PROBABILITY){
						toReceiver.send(packet);
						System.out.println("\nSending Packet, Pcket Number " + lastPacketSent);

					}else{
						System.out.println("\n[X] Lost Packet, Packet Number " + lastPacketSent);
					}


					lastPacketSent++;
					// Increase the last sent



				} // End of sending while

				// Byte array for the ACK sent by the receiver
				byte[] ackBytes = new byte[48];

				// Creating packet for the ACK
				DatagramPacket ack = new DatagramPacket(ackBytes, ackBytes.length);

				try{
					// If an ACK was not received in the time specified (continues on the catch clausule)
					toReceiver.setSoTimeout(TIMER);

					// Receive the packet
					toReceiver.receive(ack);

					// Unserialize the RDTAck object
					RDTAck ackObject = (RDTAck) Serializer.toObject(ack.getData());

					if(ackObject.getPacket()>lastSq-1) break;
					acks[ackObject.getPacket()]=1;

					System.out.println("Received ACK for " + ackObject.getPacket());

					// If this ack is for the last packet, stop the sender (Note: gbn has a cumulative acking)
					if(ackObject.getPacket() == lastSq){
						break;
					}

					w8Ack = Math.max(w8Ack, ackObject.getPacket());

				}catch(SocketTimeoutException e){
					// then send all the sent but non-acked packets
					System.out.println("Timer Expired");
					for(int i = sBase; i < sBase+WSIZE; i++){
						if(i>lastSq-1) break;
						byte[] data_Sending = Serializer.toBytes(sent.get(i));

						// Create the packet
						DatagramPacket packet = new DatagramPacket(data_Sending, data_Sending.length, receiverAddress, port );
						if(acks[i]!=1){
							// Send with some probability
							if(Math.random() > PROBABILITY){
								toReceiver.send(packet);
								System.out.println("\nResending Packet, Packet Number: " + sent.get(i).getSeq());
								System.out.println("Timer Restarted");

							}else{
								System.out.println("\n[X] Lost Packet, Packet Number: " + sent.get(i).getSeq());
							}
						}
					}
				}

				for(int i=sBase;i<sBase+WSIZE;i++){
					if(i>lastSq-1) break;
					if(acks[i]==0)
					{
						sBase=i;
						break;
					}
					if(i==sBase+WSIZE-1)
						sBase=i+1;
				}

//				ack_Cout=0;
				System.out.println("LAST SENT = "+lastPacketSent);
				System.out.println("SEND BASE = "+sBase);
				System.out.println("ACKS = ");
				for(int i=sBase;i<sBase+WSIZE;i++){				
					if(i>lastSq-1) break;
					System.out.println(acks[i]);
				}
				System.out.println("\n \n (Next Packet Transmission) \n \n"); 
			}

			System.out.println("\n\tFinished Transmission!!! :)");
		}

	}

}
