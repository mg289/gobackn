import java.net.*;
import java.util.*;
import java.io.*;

public class sender {

  // constants
  private static final int windowSize = 10;
  private static final int maxTime = 150;
  private static final int maxChars = 500;
  private static final int seqNumModulo = 32;
  
  // data members
  private int base;
  private int nextSeqNum;
  private int realSeqNum;
  private int emulatorPort;
  private int senderPort;
  private DatagramSocket dataSocket;
  private DatagramSocket ackSocket;
  private InetAddress IPAddress;
  private String fileName;
  private List<String> data;
  private Timer timer;
  
  public static void main(String args[]) throws Exception {
    // Parse command line arguments	
    String emulatorHost = args[0];
	int emulatorPort = Integer.parseInt(args[1]);
	int senderPort = Integer.parseInt(args[2]);
    String fileName = args[3];	

	new sender(emulatorHost, emulatorPort, senderPort, fileName);
  }
  
  public sender(String eHost, int ePort, int sPort, String fName) throws Exception {

  	// Set data from command line args
	IPAddress = InetAddress.getByName(eHost);
	emulatorPort = ePort;
	senderPort = sPort;
	fileName = fName;
  
	// Create Sockets	
	
	try {
	  dataSocket = new DatagramSocket();
	} catch (NumberFormatException e) {
	  e.printStackTrace();
	  System.out.println("Error with dataport and number format");
	}  catch (IOException e) {
	  e.printStackTrace();
	  System.out.println("IO exception in data connection");
	}
	
	try {
	  ackSocket = new DatagramSocket(senderPort);
	} catch(NumberFormatException e) {
	  e.printStackTrace();
	  System.out.println("Error with ackport and number format");
	} catch (IOException e) {
	  e.printStackTrace();
	  System.out.println("IO exception in ack connection");
	}
	
	// Initialize go-back-n variables
	base = 0;
	nextSeqNum = 0;
	timer = new Timer();
	
	// Create array containing data for each packet
	data = readFileData(fileName);
	send();
  }
  
  private void send() throws Exception {
  
	int maxPacket = data.size() - 1;
	int lastSeqNum = 0;
	int multiplier = 0;
	DatagramPacket dataPacket;
	DatagramPacket ackPacket;
	byte[] ackArray = new byte[1024];
	byte[] currentArray;
	packet currentData = null;
	packet currentAck = null;

	while(realSeqNum <= maxPacket) {
	  if(nextSeqNum < base + windowSize && nextSeqNum <= maxPacket) {
	    currentData = packet.createPacket(nextSeqNum, data.get(nextSeqNum));
		currentArray = currentData.getUDPdata();
		dataPacket = new DatagramPacket(currentArray, currentArray.length, IPAddress, emulatorPort);
		dataSocket.send(dataPacket);
		if(base == nextSeqNum) {
		  timer.schedule(new Timeout(), maxTime);
		}
		nextSeqNum++;
	  } else {
		ackPacket = new DatagramPacket(ackArray, ackArray.length);
		ackSocket.receive(ackPacket);
		currentAck = packet.parseUDPdata(ackPacket.getData());
		
		// Convert seqNum from modulo 32
		int newSeq = currentAck.getSeqNum();
		if(newSeq < lastSeqNum) {
		  multiplier++;
		}
		realSeqNum = newSeq + seqNumModulo*multiplier;
		lastSeqNum = newSeq;
		
		// update base and timer
		base = realSeqNum + 1;
		if(base == nextSeqNum) {
		  timer.cancel();
		} else {
		  timer.schedule(new Timeout(), maxTime);
		}
		if(realSeqNum == maxPacket) break;
	  }
    }

	// Send EOT packet
	currentData = packet.createEOT(nextSeqNum);
	currentArray = currentData.getUDPdata();
	dataPacket = new DatagramPacket(currentArray, currentArray.length, IPAddress, emulatorPort);
	dataSocket.send(dataPacket);
	
	// Wait to receive EOT Ack
	int lastAckdEOT;
	while(true) {
	    ackPacket = new DatagramPacket(ackArray, ackArray.length);
	    ackSocket.receive(ackPacket);
		lastAckdEOT = packet.parseUDPdata(ackPacket.getData()).getSeqNum();
		if(lastAckdEOT == ((maxPacket + 1)%seqNumModulo)) break;
	}

	// Close resources and exit
	dataSocket.close();
	ackSocket.close();
	timer.cancel();
  }
  
  private static List<String> readFileData(String fileName) throws Exception {
    FileReader fr = new FileReader(fileName);
    List<String> data = new ArrayList<String>();
    char[] buf = new char[maxChars];
    int pos = 0;

	// Read file and store up to maxChars chars to be sent
	try {
      while(true) {
        int read = fr.read(buf, pos, maxChars - pos);
        if (read == -1) {
          if (pos > 0) {
            data.add(new String(buf, 0, pos));
		  }
          break;
        }
        pos += read;
        if (pos == maxChars) {
          data.add(new String(buf));
          pos = 0;
        }
      }
	} catch (IOException e) {
	  e.printStackTrace();
	  System.out.println("IO exception reading " + fileName);
    } 
    return data;
  }
  
  class Timeout extends TimerTask {
    public void run() {
	  // Restart timer
      timer.schedule(new Timeout(), maxTime);
	  
	  // Resend all packets in window
	  for(int i = base; i <= nextSeqNum - 1; i++) {
	    try {
	      packet packetObject = packet.createPacket(i, data.get(i));
		  byte[] currentArray = packetObject.getUDPdata();
		  DatagramPacket dataPacket = new DatagramPacket(currentArray, currentArray.length, IPAddress, emulatorPort);
		  dataSocket.send(dataPacket);
	    } catch (Exception e) {
		  // TODO
		}
	  }
    }
  }
}
