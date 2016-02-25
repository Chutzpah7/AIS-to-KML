import java.util.*;
import java.io.*;
public class AISDecoder {
	// Need a place to hold fragments of incomplete sentances
	public static ArrayList<String> fragments = new ArrayList<String>();
	// Takes an argument of a filename, crefates buffered reader 
	public static void main(String[] args) {
		// Using try to handle I/O exceptions
		// dataBR reads from file given as an argument to program
		try (BufferedReader dataBR = new BufferedReader(new FileReader(args[0]))) {
			// Initialize string that will read lines from bufferedreader
			String NMEASentance;
			// Loop through the file
			while ((NMEASentance = dataBR.readLine()) != null) {
				processNMEASentance(NMEASentance);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void processNMEASentance(String sentance){

		System.out.println(sentance);
		// Fields in an NMEA sentance are comma separated.
		String [] fields = sentance.split("\\*")[0].split(",");

		String prefix = fields[0];

		int fragmentCount = Integer.parseInt(fields[1]);

		int fragmentNumber = Integer.parseInt(fields[2]);

		String sequentialID = fields[3];

		String channel = fields[4];

		String payload = fields[5];

		int padBits = Integer.parseInt(fields[6]);

		String checksum = sentance.split("\\*")[1];

		int sentanceSum = 0;

		for(int i = 0; i < sentance.split("\\*")[0].substring(1).length(); i++) {
			sentanceSum ^= sentance.split("\\*")[0].substring(1).charAt(i);
		}

		if (!checksum.equals(String.format("%2s", Integer.toString(sentanceSum,16).toUpperCase()).replace(" ", "0"))) {
			System.err.println("Bad checksum!");
			return;
		}

		// AI prefix is the only mobile AIS station identifier, AIVDM is from other ships, AIVDO is from one's own ship.
		// Because this will probably only be implemented on a receiver due to cost, the AIVDO prefix is extraneous.
		if (!prefix.equals("!AIVDM")) {
			System.err.println("Packet doesn't have AIVDM prefix, it is prefixed by " + prefix);
			return;
		}


		// Dealing with a fragmented sentance
		if (fragmentCount != 1) {
			// Not the last fragment of a sentance
			if (fragmentNumber < fragmentCount) {
				// Add to the arraylist of sentance fragments to be dealt with later.
				// Adding some expiration to this may improve performance.
				fragments.add(sentance);
				return;
			}
			// This must be the last fragment of a sentance
			else  {
				fragments.add(sentance);
				payload = "";
				for(int seekFragmentNum = 0; seekFragmentNum <= fragmentCount; seekFragmentNum++) {
					for(int i = 0; i < fragments.size(); i++) {
						String [] fragmentInArrayList = fragments.get(i).split(",");
						// If the sequential ID matches, the total fragment count matches, and the fragment's position in the whole sentance is the one we're looking for:
						if(Integer.parseInt(fragmentInArrayList[1]) == fragmentCount 
							&& Integer.parseInt(fragmentInArrayList[2]) == seekFragmentNum 
							&& fragmentInArrayList[3].equals(sequentialID)) {
							payload += fragmentInArrayList[5];
							fragments.remove(i);
							i--;
						}
					}
				}
	
				operateOnAISPayload(payload);
				return;
			}
		}
		// Nonfragmented sentance
		else {
			operateOnAISPayload(payload);
			return;
		}

	}

	// It might make sense to get rid of this method, and have a method that converts only needed information into fields,
	// while it would be more complex, it wouldn't be harder for the computer to do at all, and should work more nicely.
	public static void operateOnAISPayload(String payload) {

		String [] navigationStatuses = {"Under way using engine","At anchor","Not under command","Restricted manoeuverability","Constrained by her draught","Moored","Aground","Engaged in Fishing","Under way sailing","Reserved for future amendment of Navigational Status for HSC","Reserved for future amendment of Navigational Status for WIG","Reserved for future use","Reserved for future use","Reserved for future use","AIS-SART is active","Not defined"};
		
		if (payload.charAt(0) != '1' && payload.charAt(0) != '2' && payload.charAt(0) != '3') {
			return;
		}
		long messageType = getBits(0, 5, payload) & 0x3f; //unsigned
		System.out.println("Message type: " + messageType);

		long mmsi = getBits(8, 37, payload) & 0x1fffffff; //unsigned
		System.out.println("MMSI: " + mmsi);

		int navStatus = (int)getBits(38, 41, payload) & 0xf; //unsigned
		System.out.println("Navigation Status: " + navigationStatuses[navStatus]);

		long rateOfTurn = (byte)getBits(42, 49, payload); //signed, scaled byte
		if (rateOfTurn == 0)
			System.out.println("Rate of turn: 0 degrees/min - not turning");
		else if (rateOfTurn == 127)
			System.out.println("Rate of turn: No turn rate available - turning right");
		else if (rateOfTurn == -127)
			System.out.println("Rate of turn: No turn rate available - turning left");
		else if (rateOfTurn >= 1 && rateOfTurn <= 126)
			System.out.println("Rate of turn: " + Math.pow(rateOfTurn/4.733, 2) + " degrees/min - turning right");
		else if (rateOfTurn <= -1 && rateOfTurn >= -126)
			System.out.println("Rate of turn: -" + Math.pow(rateOfTurn/4.733, 2) + " degrees/min - turning left");
		else
			System.out.println("Rate of turn: Not available");

		long speedOverGround = getBits(50, 59, payload) & 0x3ff; //unsigned, scaled int
		if (speedOverGround == 1023)
			System.out.println("Speed over ground: Not available");
		else if (speedOverGround == 1022)
			System.out.println("Speed over ground: Over 102.2 knots");
		else
			System.out.println("Speed over ground: " + speedOverGround/10.0 + " knots");

		boolean positionAccuracy = (getBits(60, 60, payload)==1); //boolean
		if (positionAccuracy)
			System.out.println("Position accuracy: DGPS-quality, accurate to under 10 meters");
		else
			System.out.println("Position accuracy: Not accurate to under 10 meters");

		

		System.out.println("-----------------------");
		return;
	}
	
	public static int get6Bits(int index, String encodedData) {
		return "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVW`abcdefghijklmnopqrstuvw".indexOf(encodedData.charAt(index)); // Payload decoding
	}

	public static long getBits(int startBit, int endBit, String encodedData) {
		long concatenated = 0;
		int startByte = startBit/6;
		int endByte = endBit/6;
		int rightPad = 5 - (endBit % 6);
		for (int i = startByte; i < endByte; i++) {
			concatenated |= get6Bits(i, encodedData) << (((endByte - i)*6) - rightPad); //gets bytes needed and orders them
		}
		concatenated |= ((get6Bits(endByte,encodedData) >>> rightPad) & (int)(Math.pow(2,6-rightPad)-1));
		// concatenated = concatenated & (long)(Math.pow(2,endBit-startBit)-1);
		return concatenated;
	}
}

/*

*/