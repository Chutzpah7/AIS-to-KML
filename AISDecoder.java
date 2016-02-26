import java.util.*;
import java.io.*;
import java.math.*;
public class AISDecoder {
	public static final int KML = 0;
	public static final int CSV = 1;


	// Need a place to hold fragments of incomplete sentances
	public static ArrayList<String> fragments = new ArrayList<String>();
	public static ArrayList<DataPoint> dataPoints = new ArrayList<DataPoint>();
	public static boolean silent = false;
	public static ArrayList<String> inputFiles = new ArrayList<String>();
	public static String outputFile = "";
	public static int outputType = KML;

	// Takes an argument of a filename, crefates buffered reader 
	public static void main(String[] args) {
		//Holds information on all of the points to be written to KML
		

		// Using try to handle I/O exceptions
		// dataBR reads from file given as an argument to program
		if (args.length == 0) {
			System.out.println("Usage: AISDecoder [OPTION]... FILE\nOptions:\n\t-s, --silent\t\tdo not write anything to stdout\n\t-o, --output FILE\twrite output to a file\n\t-k, --kml\t\toutput as kml file (default)\n\t-c, --csv\t\toutput as csv file");
			System.exit(1);
		}

		for (int i = 0; i < args.length; i++) {
			if (i != 0 && (args[i-1].equalsIgnoreCase("-o") || args[i-1].equalsIgnoreCase("--output"))) {
				outputFile = args[i];
				break;
			}
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].charAt(0) == '-') {
				switch(args[i].toLowerCase()) {
					case "-s": case "--silent":
						silent = true;
						break;
					case "-c": case "--csv":
						outputType = CSV;
						break;
					case "-o": case "--output":
						break;
					default:
						System.err.println("");
						System.exit(1);
						break;
				}
			}
			else if (!args[i].equalsIgnoreCase(outputFile)){
				inputFiles.add(args[i]);
			}
		}

		for (String fileString : inputFiles) {

			try (BufferedReader dataBR = new BufferedReader(new FileReader(fileString))) {
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
		if(outputType==KML)
			writeToKML(dataPoints);
		else if(outputType==CSV)
			;

	}

	public static void processNMEASentance(String sentance) {

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
				return;
			}
		}
		// Nonfragmented sentance
		else {
			operateOnAISPayload(payload);
			return;
		}
	}


	public static void operateOnAISPayload(String payload) {

		String [] navigationStatuses = {"Under way using engine","At anchor","Not under command","Restricted manoeuverability","Constrained by her draught","Moored","Aground","Engaged in Fishing","Under way sailing","Reserved for future amendment of Navigational Status for HSC","Reserved for future amendment of Navigational Status for WIG","Reserved for future use","Reserved for future use","Reserved for future use","AIS-SART is active","Not defined"};
		
		if (payload.charAt(0) != '1' && payload.charAt(0) != '2' && payload.charAt(0) != '3') {
			return;
		}

		long mmsi = getBits(8, 37, payload); //unsigned

		int navStatus = (int) getBits(38, 41, payload); //unsigned
		String navStatusString;
		navStatusString = navigationStatuses[navStatus];

		long rateOfTurn = (getBits(42, 49, payload)); //signed scaled int
		String rateOfTurnString;
		if (rateOfTurn == 0) {
			rateOfTurnString = "0";
		}
		else if (rateOfTurn == 127){
			rateOfTurnString = "Turning right";
		}
		else if (rateOfTurn == -127){
			rateOfTurnString = "Turning left";
		}
		else if (rateOfTurn >= 1 && rateOfTurn <= 126) {
			rateOfTurnString = "" + Math.pow(rateOfTurn / 4.733, 2);
		}
		else if (rateOfTurn <= -1 && rateOfTurn >= -126) {
			rateOfTurnString = "-" + Math.pow(rateOfTurn / 4.733, 2);
		}
		else
			rateOfTurnString = "N/A";

		long speedOverGround = getBits(50, 59, payload); //unsigned, scaled int
		String sogString;
		if (speedOverGround == 1023)
			sogString = "N/A";
		else if (speedOverGround == 1022)
			sogString = "Over 102.2";
		else
			sogString = "" + speedOverGround / 10.0;

		boolean positionAccuracy = (getBits(60, 60, payload) == 1); // boolean

		

		double longitude = (getBits(61, 61, payload) == 0) ? getBits(62, 88, payload) / 600000.0 : -1 * (((getBits(62, 88, payload)) ^ 0x7ffffff) - 1) / 600000.0; //2s compliment of unsigned part of longitude multiplied by sign bit
		
		double latitude = (getBits(89, 89, payload) == 0) ? getBits(90, 115, payload) / 600000.0 : -1 * (((getBits(90, 115, payload)) ^ 0x1ffffff) - 1) / 600000.0; //pulls sign bit from latitude value

		//double latitude = (double)(latitudeSign * (((getBits(90, 115, payload) & 0x1ffffff) ^ 0x1ffffff) - 1)) / 600000.0; //2s compliment of unsigned part of latitude multiplied by sign bit

		dataPoints.add(new DataPoint(mmsi, String.format("%.6f",latitude), String.format("%.6f",longitude), navStatusString, rateOfTurnString, speedOverGround, positionAccuracy));

		return;
	}
	
	public static int get6Bits(int index, String encodedData) {
		return "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVW`abcdefghijklmnopqrstuvw".indexOf(encodedData.charAt(index)); // Payload decoding
	}

	public static long getBits(int startBit, int endBit, String encodedData) {
		long concatenated = 0;
		int startByte = startBit / 6;
		int endByte = endBit / 6;
		int rightPad = 5 - (endBit % 6);
		for (int i = startByte; i < endByte; i++) {
			concatenated |= get6Bits(i, encodedData) << (((endByte - i) * 6) - rightPad); //gets bytes needed and orders them
		}
		concatenated |= ((get6Bits(endByte, encodedData) >> rightPad) & (int)(Math.pow(2, 6 - rightPad) - 1));
		concatenated = concatenated & (long)(Math.pow(2, endBit - startBit + 1) - 1);
		return concatenated;
	}

	public static void writeToKML(ArrayList<DataPoint> points) {
		File output = new File(outputFile);
		try {
			output.createNewFile();
			output.setWritable(true);
			PrintWriter pw = new PrintWriter(output);
			pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			pw.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\" xmlns:kml=\"http://www.opengis.net/kml/2.2\" xmlns:atom=\"http://www.w3.org/2005/Atom\">");
			pw.println("<Document>");
			pw.println("\t<name>" + outputFile + "</name>");
			for (DataPoint dp : points) {
				if(!silent)
					System.out.println(dp);
				pw.println("\t<Placemark>");
				pw.println("\t\t<name>" + dp.mmsi + "</name>");
				pw.println("\t\t<Point>");
				pw.println("\t\t\t<Coordinates>" + dp.latitude + "," + dp.longitude + ",0</Coordinates>");
				pw.println("\t\t</Point>");
				pw.println("\t</Placemark>");
			}
			pw.println("</Document>");
			pw.println("</kml>");
			pw.close();
		} catch (IOException e) {e.printStackTrace();}
		
	}
}

class DataPoint {

	public long mmsi;
	public String latitude;
	public String longitude;
	public String navStatus;
	public String rateOfTurn;
	public double speedOverGround;
	public String positionAccuracy;

	public DataPoint(long mmsi, String latitude, String longitude, String navStatus, String rateOfTurn, long speedOverGround, boolean positionAccuracy) {
		this.mmsi = mmsi;
		this.latitude = latitude;
		this.longitude = longitude;
		this.navStatus = navStatus;
		this.rateOfTurn = rateOfTurn;
		this.speedOverGround = (new Long(speedOverGround)).doubleValue() / 10.0;
		this.positionAccuracy = positionAccuracy ? "DGPS-quality fix, accuracy < 10 m" : "GNSS fix, accuracy > 10m";
	}

	public String toString() {
		return "MMSI: " + mmsi + "\nLatitude: " + latitude + ", Longitude: " + longitude + "\nNavigation Status: " + navStatus + "\nRate of Turn: " + rateOfTurn + "\nSpeed Over Ground: " + speedOverGround + " knots\nPosition Accuracy: " + positionAccuracy + "\n";
		
	}
}
/*

*/
