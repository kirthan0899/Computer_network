import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class Sender {

	public static int TIMER = 1500;								//Timeout between each transmissions

	public static final double LOST_ACK_PROBABILITY = 0.05;		//Probability of occurance of acknowledgement lost

	public static final double BIT_ERROR_PROBABILITY = 0.07;	//Probability of occurance of checksum error

	public static void main(String[] args) {

		//Initialise all variables
		BufferedReader br = null;
		String fileName = "";
		int portNumber = 0;
		int numPackets = 0;
		String type = "";
		int sequenceNumBits = 0;
		int windowSize = 0;
		long timeOut = 0;
		long sizeSegment = 0;

		//Check if all parameters are entered
		if (args.length == 3) {
			fileName = args[0];
			portNumber = Integer.parseInt(args[1]);
			numPackets = Integer.parseInt(args[2]);

		} else {
			System.out.println("Invalid Parameters");
			System.exit(0);
		}
		try {
			br = new BufferedReader(new FileReader(fileName));
			String line = br.readLine();
			int i = 0;
			while (line != null) {
				if (i == 0) {
					type = line.trim();
				} else if (i == 1) {
					sequenceNumBits = Integer.parseInt(line.charAt(0) + "");
					windowSize = Integer.parseInt(line.charAt(2) + "");
				} else if (i == 2) {
					timeOut = Long.parseLong(line);

				} else if (i == 2) {
					timeOut = Long.parseLong(line);

				} else if (i == 3) {
					sizeSegment = Long.parseLong(line);

				}
				i++;
				line = br.readLine();
			}
			br.close();
		} catch (Exception e) {

			System.out.println("Error occured while reading file");
		}

		System.out.println("\nType: " + type + ", Number of Seq bits: " + sequenceNumBits + ", Window Size: " + windowSize
				+ ", Timeout: " + timeOut + ", Segment Size: " + sizeSegment + "\n");

		TIMER = (int) timeOut;
		
		//Sending Initial Data
		try {
			sendData(portNumber, numPackets, type, sequenceNumBits, windowSize, timeOut, sizeSegment);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//Sending Data
	private static void sendData(int portNumber, int numPackets, String type, int sequenceNumBits, int windowSize,
			long timeOut, long sizeSegment) throws IOException, ClassNotFoundException, InterruptedException {

		ArrayList<SegmentData> sent = new ArrayList<>();

		int lastSent = 0;

		int waitingForAck = 0;

		String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";	//Data to be sent is randomly chosen from this String
		int N = alphabet.length();

		DatagramSocket Socket = null;
		if (type.equalsIgnoreCase("gbn")) {

			//Initiate transfer
			byte[] incomingData = new byte[1024];
			InitiateTransfer initiateTransfer = new InitiateTransfer();
			initiateTransfer.setType(0);
			initiateTransfer.setNumPackets(numPackets);
			initiateTransfer.setPacketSize(sizeSegment);
			initiateTransfer.setWindowSize(1);

			//Create a socket over localhost to transfer data
			Socket = new DatagramSocket();
			InetAddress IPAddress = InetAddress.getByName("localhost");

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(outputStream);
			os.writeObject(initiateTransfer);
			byte[] data1 = outputStream.toByteArray();

			DatagramPacket initialPacket = new DatagramPacket(data1, data1.length, IPAddress, portNumber);
			System.out.println("Sending Initial Data: " + "\n");
			Socket.send(initialPacket);

			DatagramPacket initialAck = new DatagramPacket(incomingData, incomingData.length);
			Socket.receive(initialAck);
			byte[] dataImp = initialAck.getData();
			ByteArrayInputStream inReturn = new ByteArrayInputStream(dataImp);
			ObjectInputStream isReturn = new ObjectInputStream(inReturn);
			InitiateTransfer initiateTransfer2 = (InitiateTransfer) isReturn.readObject();

			if (initiateTransfer2.getType() == 100) {

				while (true) {

					while (lastSent - waitingForAck < windowSize && lastSent < numPackets) {
						
						//Send the data and hashcode to the receiver
						Random r = new Random();
						char ch = alphabet.charAt(r.nextInt(N));
						int hashCode = ("" + ch).hashCode();
						SegmentData segmentData = new SegmentData();
						segmentData.setPayLoad(ch);
						segmentData.setSeqNum(lastSent);
						segmentData.setCheckSum(hashCode);
						if (lastSent == numPackets - 1) {
							segmentData.setLast(true);
						}
						
						//Check for bit error
						if (Math.random() <= BIT_ERROR_PROBABILITY) {
							segmentData.setPayLoad(alphabet.charAt(r.nextInt(N)));
						}
						outputStream = new ByteArrayOutputStream();
						os = new ObjectOutputStream(outputStream);
						os.writeObject(segmentData);
						byte[] data = outputStream.toByteArray();

						//Start sending Packets
						DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
						System.out.println("Sending Packet: " + segmentData.getSeqNum() + ";" + " Timer Started for Packet: " + segmentData.getSeqNum());
						sent.add(segmentData);
						Socket.send(sendPacket);
						lastSent++;
						Thread.sleep(500);

					}
					
					//Receive Acknowledgement
					DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
					try {
						Socket.setSoTimeout(TIMER);
						Socket.receive(incomingPacket);
						byte[] data = incomingPacket.getData();
						ByteArrayInputStream in = new ByteArrayInputStream(data);
						ObjectInputStream is = new ObjectInputStream(in);
						AckData ackData = (AckData) is.readObject();

						if (Math.random() > LOST_ACK_PROBABILITY) {
							System.out.println("\n Received ACK for: " + (ackData.getAckNo() - 1));
							waitingForAck = Math.max(waitingForAck, ackData.getAckNo());
						} else {
							System.out.println("\n Acknowledgment Lost for: " + (ackData.getAckNo() - 1));
						}

						if (ackData.getAckNo() == numPackets) {
							break;
						}
					}
					
					//Check for Time-out
					catch (SocketTimeoutException e) {

						System.out.println("\n Timeout Occured for Packet: " + waitingForAck);

						for (int i = waitingForAck; i < lastSent; i++) {

							SegmentData segmentData = sent.get(i);
							char ch = segmentData.getPayLoad();
							int hashCode = ("" + ch).hashCode();
							segmentData.setCheckSum(hashCode);

							if (Math.random() <= BIT_ERROR_PROBABILITY) {
								Random r = new Random();
								segmentData.setPayLoad(alphabet.charAt(r.nextInt(N)));

							}
							outputStream = new ByteArrayOutputStream();
							os = new ObjectOutputStream(outputStream);
							os.writeObject(segmentData);
							byte[] data = outputStream.toByteArray();

							//Re-send Packets due to faulty network behaviour
							DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
							System.out.println(
									"Re-Sending Packet: " + segmentData.getSeqNum() + ";" + " Timer Started for Packet: " + segmentData.getSeqNum());
							Socket.send(sendPacket);
							Thread.sleep(500);
						}
					}
				}
			}
			System.out.println("\nEND\n");
		}
	}
}
