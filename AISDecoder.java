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
    	if (payload.charAt(0) != '1' && payload.charAt(0) != '2' && payload.charAt(0) != '3') {
    		return;
    	}
    	int [] decodedBinary = getBits(payload);
    	int messageType = decodedBinary[0] >> 2;
    	System.out.println("Message type: " + messageType);
    	return;
    }
    
    /*This method should eventually just take in an AIS payload and convert the 6-bit ascii into a meaningful binary value*/
    public static int[] getBits(String encodedData) {
    	// The indices of characters in this string represent the binary value each character represents
    	String payloadArmoring = "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVW`abcdefghijklmnopqrstuvw";
		
		// Converting a string from 6bit ascii to 8bit values. 
		int [] decodedData = new int[encodedData.length() * 6 / 8];
		// Separate index for the array of bytes
		int byteArrayIndex = 0;
		// 4 characters being converted into 3 bytes
		int fromChar0;
		int fromChar1;
		int fromChar2;
		int fromChar3;
		// Loop through each 4 characters in the encoded data
		for (int i = 0; i < encodedData.length(); i+=4) {
			
			// Uses payloadArmoring string to decode, documentation on AIVDM Payload Armoring can be found at http://catb.org/gpsd/AIVDM.html
			fromChar0 = payloadArmoring.indexOf(encodedData.charAt(i));
			fromChar1 = payloadArmoring.indexOf(encodedData.charAt(i+1));
			fromChar2 = payloadArmoring.indexOf(encodedData.charAt(i+2));
			fromChar3 = payloadArmoring.indexOf(encodedData.charAt(i+3));
			
			decodedData[byteArrayIndex] = (
				((fromChar0 << 2) & 0b11111100 ) | 
				((fromChar1 >>> 4) & 0b00000011 )
			);
			decodedData[byteArrayIndex+1] = (
				((fromChar1 << 4) & 0b11110000) |
				((fromChar2 >>> 2) & 0b00001111)
			);
			decodedData[byteArrayIndex+2] = (
				((fromChar2 << 6) & 0b11000000) |
				((fromChar3 >>> 0) & 0b00111111)
			);
			
			byteArrayIndex += 3;
			

		}
		return decodedData;
    }
}