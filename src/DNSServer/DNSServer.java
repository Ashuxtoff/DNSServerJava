package DNSServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import answerItem.AnswerItem;
import answerParser.DNSAnswerParser;
import cache.DNSServerCache;
import tuples.Tuple3;

public class DNSServer {
	
	private int PORT = 53;
	private String forwarder;
	private DNSServerCache cache;
	private DatagramSocket forwarderSocket;
	
	private byte[] cutBuffer(byte[] input) {
		int counter = 0;
		for (int i = input.length - 1; i > 0; i --) {
			if (input[i] != 0) {
				counter = i + 1;
				break;
			}
		}
		return Arrays.copyOfRange(input, 0, counter);
	}
	
	
	public DNSServer() {
		File config = new File("C:\\Users\\Александр\\eclipse-workspace\\DNSServer\\config.txt");
		File backup = new File("C:\\Users\\Александр\\eclipse-workspace\\DNSServer\\backup.txt");
		try {
			FileReader configFileReader = new FileReader(config);
			BufferedReader configReader = new BufferedReader(configFileReader);
			String line = configReader.readLine();
			this.forwarder = line.split(" ")[1];
			this.cache = new DNSServerCache(backup);
			this.forwarderSocket = new DatagramSocket();
			this.forwarderSocket.connect(new InetSocketAddress(InetAddress.getByName(this.forwarder), PORT));
			configReader.close();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String run(String type, String target) {
		ArrayList<String> cacheData = this.cache.getData(target, type);
		if (cacheData != null) {
			StringBuilder sb = new StringBuilder();
			for (String item : cacheData) {
				sb.append(item);
				sb.append(" ");
			}
			return sb.toString();
		}
		
		else {
			Random randomizer = new Random();
			int id = randomizer.nextInt(99);
			byte[] firstPart = new byte[] {0, (byte)id, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0};
			String[] splittedTarget = target.split("\\.");
			int namePartLenght = 0;
			for (String item : splittedTarget)
				namePartLenght += 1 + item.length();
			byte[] secondPart = new byte[namePartLenght + 5];
			int index = 0;
			for (String item : splittedTarget) {
				secondPart[index] = (byte)item.length();
				index ++;
				for (int i = 0; i < item.length(); i++) {
					secondPart[index] = (byte)item.charAt(i);
					index ++;
				}
			}
			byte typeByte = 0;
			if (type.equals("A"))
				typeByte = 1;
			else
				typeByte = 2;
			byte[] finalSecondPart = new byte[] {0, 0, typeByte, 0, 1};
			for (int i = 0; i < finalSecondPart.length; i++) {
				secondPart[index] = finalSecondPart[i];
				index ++;
			}
			byte[] query = new byte[firstPart.length + secondPart.length];
			for (int i = 0; i < query.length; i++) {
				if (i >= firstPart.length)
					query[i] = secondPart[i - firstPart.length];
				else
					query[i] = firstPart[i];
			}
			DatagramPacket resendPacket;
			try {
				resendPacket = new DatagramPacket(
					query, query.length, new InetSocketAddress(
						InetAddress.getByName(this.forwarder), PORT));
				this.forwarderSocket.send(resendPacket);
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
			byte[] responseBuffer = new byte[512];
			DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
			try {
				forwarderSocket.receive(response);
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
			responseBuffer = cutBuffer(responseBuffer);
			responseBuffer[2] = (byte)129;
			DNSAnswerParser resultParser = new DNSAnswerParser(responseBuffer);
			Tuple3<HashMap<String, ArrayList<AnswerItem>>, String, String> dataTuple3 = resultParser.extractData();
			this.cache.addData(dataTuple3);
			ArrayList<String> resultList = cache.getData(target, type);
			StringBuilder sb = new StringBuilder();
			for (String item : resultList) {
				sb.append(item);
				sb.append(" ");
			}
			return sb.toString();
		}		
	}	
}
