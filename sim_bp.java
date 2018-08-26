import java.io.File;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Scanner;

/* Michael Patel
 * ECE 521
 * Project 2: Dynamic Branch Prediction
 * Spring 2017
 * 
 * 3 models of branch prediction:
 * 1. Bimodal
 * 2. G-share
 * 3. Hybrid

 */

public class sim_bp { // lowercase b/c that's what TA asked for in spec

	// GLOBALS, Class Variables
	static String model; // bimodal, gshare, hybrid
	static int iB; // #PC bits used to index bimodal table
	static int iG; // #PC bits used to index gshare table
	static int h; // #GBHR bits used to index gshare table
	static int k; // #PC bits used to index the chooser history table
	static String tracefile; // filename of input trace

	static String inAddress;
	static String operation;
	final static int TAKEN = 1;
	final static int NOT_TAKEN = 0;
	static int actual; // actual outcome
	static int B_BHT[];
	static int G_BHT[];
	static int CHT[];	
	static int GBHR;

	static int bimodalPrediction;
	static int gsharePrediction;
	static int hybridPrediction;

	static int numPredictions;
	static int numMisPredictions;
	static double misPredictionRate;	
	static DecimalFormat format = new DecimalFormat("#0.00"); // format to 2 decimal places
	
	static int numBimodalMisses;
	static int numGshareMisses;
	static int numHybridMisses;
	
	final static String COMMAND = "COMMAND";
	final static String OUTPUT = "OUTPUT";
	static String argOutput;

	/*********************************************************************************************/
	// Constructor
	public sim_bp(String[] args){
		initializeVars(); // reset
		model = args[0]; // end all, be all		

		// different models have different number of input arguments
		switch(model){
		case "bimodal":
			iB = Integer.parseInt(args[1]);
			tracefile = args[2];
			argOutput = "./sim_bp bimodal " + iB + " " + "traces/" + tracefile;
			break;

		case "gshare":
			iG = Integer.parseInt(args[1]);
			h = Integer.parseInt(args[2]);
			tracefile = args[3];
			argOutput = "./sim_bp gshare " + iG + " " + h + " " + "traces/" + tracefile;
			break;

		case "hybrid":
			k = Integer.parseInt(args[1]);
			iG = Integer.parseInt(args[2]);
			h = Integer.parseInt(args[3]);
			iB = Integer.parseInt(args[4]);
			tracefile = args[5];
			argOutput = "./sim_bp hybrid " + k + " " + iG + " " + h + " " + iB + " " + "traces/" + tracefile;
			break;

		default:break;
		}

		// Initialization
		B_BHT = new int[(int) Math.pow(2, iB)]; // set size of B_BHT
		G_BHT = new int[(int) Math.pow(2, iG)]; // set size of G_BHT
		CHT = new int[(int) Math.pow(2, k)]; // set size of CHT
		initializeTables(); // initialize count values inside the tables

	} // end of constructor

	/*********************************************************************************************/
	// Main
	public static void main(String[] args){		
		sim_bp bp = new sim_bp(args); // call constructor to instantiate new sim_bp object

		try{
			// read input from tracefile
			Scanner scanner = new Scanner(new File(tracefile));

			while(scanner.hasNext()){ // tokens still available to read, i.e. not end of file

				inAddress = scanner.next().trim(); // read in address (24 bits long)
				operation = scanner.next().trim(); // read in operation (t/n - 1 char long)

				// set actual branch outcome
				if(operation.equals("t")){
					actual = TAKEN;
				}
				else if(operation.equals("n")){
					actual = NOT_TAKEN;
				}
				else{
					// do nothing
				}

				//
				numPredictions++; // make prediction for each address read in

				/* different models: 
				 * 		parse the address differently
				 * 		determine predictions differently
				 * 		update different tables (B_BHT v G_BHT v CHT)
				 * 		display final contents of tables to console differently
				 * 		etc.
				 */
				switch(model){
				case "bimodal":
					bimodalPrediction = runBimodal();
					boolean isPredictionCorrect_B = comparePredAndActual(bimodalPrediction, actual);
					updateMisPredCount("bimodal", isPredictionCorrect_B);
					int index_B = getIndex("bimodal", iB, iG, h, k, inAddress);
					updateTable(B_BHT, index_B, actual);
					break;

				case "gshare":
					gsharePrediction = runGshare();	
					boolean isPredictionCorrect_G = comparePredAndActual(gsharePrediction, actual);
					updateMisPredCount("gshare", isPredictionCorrect_G);
					int index_G = getIndex("gshare", iB, iG, h, k, inAddress);
					updateTable(G_BHT, index_G, actual);
					if(h != 0){
						updateGBHR(h, actual);
					}					
					break;

				case "hybrid":
					hybridPrediction = runHybrid();
					boolean isPredictionCorrect = comparePredAndActual(hybridPrediction, actual);
					updateMisPredCount("hybrid", isPredictionCorrect);	
					if(h != 0){
						updateGBHR(h, actual); // update shift register regardless
					}					
					int index_H = getIndex("hybrid", iB, iG, h, k, inAddress);
					updateCHT("hybrid", index_H); // update count value in CHT (Hybrid only)
					break;

				default: break;
				}

			} // end of while loop

			scanner.close();

		} catch(Exception e){
			e.printStackTrace();
			//System.out.println(args[0]);
			//System.out.println(args[1]);
			//System.out.println(args[2]);
		}

		getMisPredictionRate(model);
		generateOutput(model); // generate console output

	} // end of Main

	/*********************************************************************************************/
	public static int runBimodal(){ // use B_BHT
		String tempModel = "bimodal";
		int index = getIndex(tempModel, iB, iG, h, k, inAddress);
		int count = getTableCount(B_BHT, index);
		int prediction = getPrediction(tempModel, count);
		return prediction; // return the bimodal prediction
	}

	/*********************************************************************************************/
	public static int runGshare(){ // use G_BHT		
		String tempModel = "gshare";
		int index = getIndex(tempModel, iB, iG, h, k, inAddress);
		int count = getTableCount(G_BHT, index);
		int prediction = getPrediction(tempModel, count);
		return prediction; // return gshare prediction
	}

	/*********************************************************************************************/
	public static int runHybrid(){
		String tempModel = "hybrid";
		
		bimodalPrediction = runBimodal();
		gsharePrediction = runGshare();

		int H_index = getIndex(tempModel, iB, iG, h, k, inAddress); // Hybrid
		int count = getTableCount(CHT, H_index); // Hybrid
		int prediction = getPrediction(tempModel, count); // Hybrid, which prediction will be selected?		
		return prediction;
	}	

	/*********************************************************************************************/
	public static int getIndex(String model, int iB, int iG, int h, int k, String address){
		String binary = HexToBinary(address); // Hex to Binary String

		switch(model){
		case "bimodal":			
			return BinaryStringToDecimal(binary.substring(binary.length() - (iB+2), binary.length() - 2));	// Binary String to decimal int

		case "gshare":
			if(h != 0){
				int PC_h_chunk = BinaryStringToDecimal(binary.substring(binary.length() - (iG+2),  binary.length() - (iG+2) + h));
				String PC_i_chunk = binary.substring(binary.length() - (iG+2) + h, binary.length() - 2);

				String MS_idx = Integer.toBinaryString(GBHR ^ PC_h_chunk); // XOR
				String idx = MS_idx + PC_i_chunk; // concatenate to form an index string
				return Integer.parseInt(idx, 2); // return int of the binary string
			}
			else{ // h = 0
				return BinaryStringToDecimal(binary.substring(binary.length() - (iG+2), binary.length() - 2));	// Binary String to decimal int
			}
			

		case "hybrid":
			return BinaryStringToDecimal(binary.substring(binary.length() - (k+2), binary.length() - 2));

		default: return 0;
		}		
	}

	/*********************************************************************************************/
	public static int getTableCount(int table[], int idx){
		return table[idx];
	}

	/*********************************************************************************************/
	public static int getPrediction(String model, int count){
		switch(model){
		case "bimodal":
			// assume count is either 0, 1, 2 or 3 b/c bimodal is 2-bit
			if( (count == 2) || (count == 3) ){
				return TAKEN;
			}
			else{ // count is 0 or 1
				return NOT_TAKEN;
			}

		case "gshare":
			// assume count is either 0, 1, 2 or 3
			if( (count == 2) || (count == 3) ){
				return TAKEN;
			}
			else{ // count is 0 or 1
				return NOT_TAKEN;
			}

		case "hybrid":
			// assume count is either 0, 1, 2 or 3
			if( (count == 2) || (count == 3) ){
				int index = getIndex("gshare", iB, iG, h, k, inAddress);
				updateTable(G_BHT, index, actual);
				return gsharePrediction;
			}
			else{ // count is 0 or 1
				int index = getIndex("bimodal", iB, iG, h, k, inAddress);
				updateTable(B_BHT, index, actual);
				return bimodalPrediction;
			}

		default: return 0;
		}
	}

	/*********************************************************************************************/
	public static boolean comparePredAndActual(int prediction, int actual){
		if(prediction == actual){
			return true;
		}
		else{ // prediction != actual
			return false;
		}
	}

	/*********************************************************************************************/
	public static void updateTable(int table[], int index, int actual){
		if( (actual == TAKEN) && (table[index] < 3) ){ // increment if actual outcome was taken, but saturate at 3
			table[index]++;
		}
		if( (actual == NOT_TAKEN) && (table[index] > 0) ){ // decrement if actual outcome was not taken, but saturate at 0
			table[index]--;
		}
	}

	/*********************************************************************************************/
	public static void updateGBHR(int h, int actual){
		GBHR = GBHR >> 1; // shift out the LSb
		int mask = actual << (h-1); // create a bit mask for the actual outcome
		GBHR |= mask;
	}
	
	/*********************************************************************************************/
	public static void updateCHT(String model, int index){
		boolean isBimodalCorrect = comparePredAndActual(bimodalPrediction, actual);
		boolean isGshareCorrect = comparePredAndActual(gsharePrediction, actual);
		
		if(isBimodalCorrect && !isGshareCorrect){
			updateTable(CHT, index, NOT_TAKEN); // decrement
		}
		else if(!isBimodalCorrect && isGshareCorrect){
			updateTable(CHT, index, TAKEN); // increment
		}
		else{
			// do nothing
		}
	}

	/*********************************************************************************************/
	public static void updateMisPredCount(String model, boolean isCorrect){
		switch(model){
		case "bimodal":
			if(!isCorrect){
				numBimodalMisses++;
			}
			break;
			
		case "gshare":
			if(!isCorrect){
				numGshareMisses++;
			}
			break;
			
		case "hybrid":
			if(!isCorrect){
				numHybridMisses++;
			}
			break;
		}		
	}

	/*********************************************************************************************/	
	public static void getMisPredictionRate(String model){
		switch(model){
		case "bimodal":
			numMisPredictions = numBimodalMisses;
			break;
			
		case "gshare":
			numMisPredictions = numGshareMisses;
			break;
			
		case "hybrid":
			numMisPredictions = numHybridMisses;
			break;
		}
		misPredictionRate = (double) ((float) numMisPredictions / (float) numPredictions)*100;
	}

	/*********************************************************************************************/
	public static String HexToBinary(String in){
		return String.format("%24s", new BigInteger(in, 16).toString(2)).replace(' ', '0'); // pad the binary string to length=24
	}

	/*********************************************************************************************/
	public static int BinaryStringToDecimal(String in){
		//System.out.println(in.length());
		return Integer.parseInt(in, 2);		 
	}

	/*********************************************************************************************/
	public void initializeVars(){
		model = "";
		iB = iG = h = k = 0;
		GBHR = 0;
		tracefile = "";
		numPredictions = 0;
		numMisPredictions = 0;
		actual = 0;		
		numBimodalMisses = 0;
		numGshareMisses = 0;
		numHybridMisses = 0;
	}

	/*********************************************************************************************/
	public void initializeTables(){

		// B_BHT -> initialize with all 2s
		for(int i=0; i<B_BHT.length; i++){
			B_BHT[i] = 2;
		}

		// G_BHT -> initialize with all 2s
		for(int i=0; i<G_BHT.length; i++){
			G_BHT[i] = 2;
		}

		// CHT -> initialize with all 1s
		for(int i=0; i<CHT.length; i++){
			CHT[i] = 1;
		}
	}

	/*********************************************************************************************/
	// Output
	public static void generateOutput(String model){
		System.out.println(COMMAND);
		System.out.println(argOutput);
		System.out.println(OUTPUT);
		System.out.println("number of predictions: " + numPredictions);
		System.out.println("number of mispredictions: " + numMisPredictions);
		System.out.println("misprediction rate: " + format.format(misPredictionRate) + "%");

		switch(model){
		case "bimodal": // print final contents of bimodal BHT
			printBimodalOutput();
			break;

		case "gshare": // print final contents of gshare BHT
			printGshareOutput();
			break;

		case "hybrid": // print final contents of chooser CHT, gshare BHT and bimodal BHT
			printHybridOutput();			
			break;

		default: break;
		}		
	}
	
	/*********************************************************************************************/
	public static void printBimodalOutput(){
		System.out.println("FINAL BIMODAL CONTENTS");
		for(int i=0; i<B_BHT.length; i++){
			System.out.println(i + "   " + B_BHT[i]);
		}
	}
	
	/*********************************************************************************************/
	public static void printGshareOutput(){
		System.out.println("FINAL GSHARE CONTENTS");
		for(int i=0; i<G_BHT.length; i++){
			System.out.println(i + "   " + G_BHT[i]);
		}
	}
	
	/*********************************************************************************************/
	public static void printHybridOutput(){
		System.out.println("FINAL CHOOSER CONTENTS");
		for(int i=0; i<CHT.length; i++){
			System.out.println(i + "   " + CHT[i]);
		}
		printGshareOutput();
		printBimodalOutput();
	}
	


} // end of class
