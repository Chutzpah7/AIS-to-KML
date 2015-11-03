import java.util.*;
import java.io.*;
public class AISDecoder {
    // Takes an argument of a filename, crefates buffered reader 
    public static void main(String[] args) {
    	
		// Using try to handle I/O exceptions
		// dataBR reads from file given as an argument to program
		try (BufferedReader dataBR = new BufferedReader(new FileReader(args[0]))) {
			// Initialize string that will read lines from bufferedreader
		    String AISDataString;
		    // Loop through the file
		    int i = 1;
		    while ((AISDataString = dataBR.readLine()) != null) {
				System.out.print(i + " : ");
				int [] binaryPayload = getBits(AISDataString);
				for (int b : binaryPayload) 
					System.out.print(String.format("%8s", Integer.toBinaryString(b)).replace(' ','0') + ' ');
				System.out.println();
				i++;

		    }
		} catch (IOException e) {
		    e.printStackTrace()
;		}
    }
    
    public static int[] getBits(String inputData) {
    	// The indices of characters in this string represent the binary value each character represents
    	String payloadArmoring = "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVW`abcdefghijklmnopqrstuvw";
		
		// Commas are used to delimit the string
		String [] values = inputData.split(",");

		if (!values[0].equals("!AIVDM")) {
		    // Not an NMEA formatted Received Data Message
		    System.err.println("Not AIVDM formatting: " + values[0]);
		    return null;
		}
		// Interested in this value, the actual data
		String encodedAISData = values[5];

		int [] binaryAISPayload = new int[encodedAISData.length() * 6 / 8];

		int byteArrayIndex = 0;
		int fromChar0;
		int fromChar1;
		int fromChar2;
		int fromChar3;
		// Loop through each 4 characters in the encoded data
		for (int i = 0; i < encodedAISData.length(); i+=4) {
			
			// Uses payloadArmoring string to decode, documentation on AIVDM Payload Armoring can be found at http://catb.org/gpsd/AIVDM.html
			fromChar0 = payloadArmoring.indexOf(encodedAISData.charAt(i));
			fromChar1 = payloadArmoring.indexOf(encodedAISData.charAt(i+1));
			fromChar2 = payloadArmoring.indexOf(encodedAISData.charAt(i+2));
			fromChar3 = payloadArmoring.indexOf(encodedAISData.charAt(i+3));
			
			binaryAISPayload[byteArrayIndex] = (
				((fromChar0 << 2) & 0b11111100 ) | 
				((fromChar1 >>> 4) & 0b00000011 )
			);
			binaryAISPayload[byteArrayIndex+1] = (
				((fromChar1 << 4) & 0b11110000) |
				((fromChar2 >>> 2) & 0b00001111)
			);
			binaryAISPayload[byteArrayIndex+2] = (
				((fromChar2 << 6) & 0b11000000) |
				((fromChar3 >>> 0) & 0b00111111)
			);
			
			byteArrayIndex += 3;
			

		}
		return binaryAISPayload;
    }
}

// 00000100 01001011 01010111 00101111 01110100 00000000 00001100 01001101 11001000 11101110 11000011 00011100 00100111 00010010 10101001 00101110 01110101 10001110 00000000 01100000 10010100
// 00000100 01001011 01010111 01110000 10100100 00000000 00000000 00001101 11001000 11011110 00011010 00011100 01110111 00100111 11101011 11000011 01010101 11010000 00000000 10000110 00011011 