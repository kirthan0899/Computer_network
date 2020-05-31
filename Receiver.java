import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;

public class Receiver {

	public static final double LOST_PACK_PROBABILITY = 0.07;	//Probability of occurance of packet lost

	public static void main(String[] args) {

		//Initialise all variables
		DatagramSocket socket = null;
		int portNumber = 0;
		
		//Check if all parameters are entered
		if (args.length == 1) {

			portNumber = Integer.parseInt(args[0]);

		} else {
			System.out.println("Invalid Parameters");
			System.exit(0);
		}
		
		//Create a socket over localhost to receive data
		byte[] incomingData = new byte[1024];
		try {
			socket = new DatagramSocket(portNumber);
			System.out.println("\nReciver Side is Ready to Accept Packets at PortNumber: " + portNumber + "\n");

			//Receiving Initial Data
			DatagramPacket initialPacket = new DatagramPacket(incomingData, incomingData.length);
			socket.receive(initialPacket);
			byte[] data1 = initialPacket.getData();
			ByteArrayInputStream inInitial = new ByteArrayInputStream(data1);
			ObjectInputStream isInitial = new ObjectInputStream(inInitial);
			InitiateTransfer initiateTransfer = (InitiateTransfer) isInitial.readObject();
			System.out.println("Initial Data Recieved = " + initiateTransfer.toString());

			int type = initiateTransfer.getType();
			InetAddress IPAddress = initialPacket.getAddress();
			int port = initialPacket.getPort();
			initiateTransfer.setType(100);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(outputStream);
			os.writeObject(initiateTransfer);

			byte[] replyByte = outputStream.toByteArray();
			DatagramPacket replyPacket = new DatagramPacket(replyByte, replyByte.length, IPAddress, port);
			socket.send(replyPacket);

			if (type == 0) {
				initiateTransfer.setType(0);
				gbnTransfer(socket, initiateTransfer);
			}

		} catch (Exception e) {

			e.printStackTrace();
		}

	}

	//Receive Packets and send Acknowledgement
	private static void gbnTransfer(DatagramSocket socket, InitiateTransfer initiateTransfer)
			throws IOException, ClassNotFoundException {

		ArrayList<SegmentData> received = new ArrayList<>();
		boolean end = false;
		int waitingFor = 0;
		byte[] incomingData = new byte[1024];

		while (!end) {
		
			//Receive incoming packet
			DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
			socket.receive(incomingPacket);
			byte[] data = incomingPacket.getData();
			ByteArrayInputStream in = new ByteArrayInputStream(data);
			ObjectInputStream is = new ObjectInputStream(in);
			SegmentData segmentData = (SegmentData) is.readObject();
			System.out.println(" \n Packet Received  = " + segmentData.getSeqNum());

			char ch = segmentData.getPayLoad();
			int hashCode = ("" + ch).hashCode();
			boolean checkSum = (hashCode == segmentData.getCheckSum());

			//Check if Checksum is correct
			if (!checkSum) {
				System.out.println("Error Occured in the Data");
			}

			//Check if the last packet
			if (segmentData.getSeqNum() == waitingFor && segmentData.isLast() && checkSum) {
				waitingFor++;
				received.add(segmentData);
				System.out.println("Last packet received");
				end = true;
			} else if (segmentData.getSeqNum() == waitingFor && checkSum) {
				waitingFor++;
				received.add(segmentData);
			}

			//Checksum error detection
			else if (!checkSum) {
				System.out.println("Checksum Error");
				segmentData.setSeqNum(-1000);
			}

			//Discard packet if they are not in order
			else {
				System.out.println("Packet discarded (not in order)");
				segmentData.setSeqNum(-1000);
			}

			//Send respective Acknowledgement
			InetAddress IPAddress = incomingPacket.getAddress();
			int port = incomingPacket.getPort();

			AckData ackData = new AckData();
			ackData.setAckNo(waitingFor);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(outputStream);
			os.writeObject(ackData);

			byte[] replyByte = outputStream.toByteArray();
			DatagramPacket replyPacket = new DatagramPacket(replyByte, replyByte.length, IPAddress, port);

			if (Math.random() > LOST_PACK_PROBABILITY && segmentData.getSeqNum() != -1000) {
				String reply = "Sending Acknowledgment Number: " + ackData.getAckNo();
				System.out.println(reply);
				socket.send(replyPacket);
			} else if (segmentData.getSeqNum() != -1000) {
				int length = received.size();
				System.out.println("Packet Lost");
				received.remove(length - 1);
				waitingFor--;
				if (end) {
					end = false;
				}
			}
		}
	}
}
