package DNSServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import answerItem.AnswerItem;
import answerMaker.DNSAnswerMaker;
import answerParser.DNSAnswerParser;
import cache.DNSServerCache;
import tuples.Tuple;
import tuples.Tuple3;

public class DNSServer {
	
	private String HOST = "localhost";
	private int PORT = 53;
	private String forwarder;
	private DNSServerCache cache;
	private DatagramSocket socket;
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
	
	private int getIntFrom(byte[] bytes, int index) {
		return (int)bytes[index];
	}
	
	private String getStringFrom(byte[] bytes, int start, int end) {
		return new String(Arrays.copyOfRange(bytes, start, end));
	}
	
	private Tuple<String, String> extractRequestData(byte[] request){
		ArrayList<String> labels = new ArrayList<String>();
		byte[] query = Arrays.copyOfRange(request, 12, request.length);
		int cursor = 0;
		int labelLength = getIntFrom(query, cursor);
		while (labelLength != 0) {
			cursor ++;
			labels.add(getStringFrom(query, cursor, cursor + labelLength));
			cursor += labelLength;
			labelLength = getIntFrom(query, cursor);
		}
		String name = String.join(".", labels);
		cursor += 2;
		String type = "";
		if (query[cursor] == 1)
			type = "A";
		else if (query[cursor] == 2)
			type = "NS";
		else System.out.print("Неверный тип записи");
		return new Tuple<String, String>(name, type);
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
			this.socket = new DatagramSocket(PORT);
//			this.socket.bind(new InetSocketAddress(PORT));
			this.forwarderSocket = new DatagramSocket();
			this.forwarderSocket.connect(new InetSocketAddress(InetAddress.getByName(this.forwarder), PORT));
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
		byte[] buffer = new byte[512];
		while (true) {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			try {
				socket.receive(packet);
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
			byte[] query = cutBuffer(buffer);
			DNSAnswerParser parser = new DNSAnswerParser(query);
			Tuple<String, String> nameType = this.extractRequestData(query);
			ArrayList<String> cacheData = this.cache.getData(nameType.value1, nameType.value2);
			if (cacheData != null) {
				DNSAnswerMaker answerMaker = new DNSAnswerMaker(query, this.cache);
				byte[] response = answerMaker.makePackage();
				DatagramPacket answerPacket;
				try {
					answerPacket = new DatagramPacket(
						response, response.length, new InetSocketAddress(
							InetAddress.getByName(HOST), PORT));
					this.socket.send(answerPacket);
				} 
				catch (IOException e) {
					e.printStackTrace();
				}				
			}
			else {
				
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
				DatagramPacket sendResponse;
				try {
					sendResponse = new DatagramPacket(
							responseBuffer, responseBuffer.length,
							new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT));
					this.socket.send(sendResponse);
				} 
				catch (IOException e) {
					e.printStackTrace();
				}
				DNSAnswerParser resultParser = new DNSAnswerParser(responseBuffer);
				Tuple3<HashMap<String, ArrayList<AnswerItem>>, String, String> dataTuple3 = resultParser.extractData();
				this.cache.addData(dataTuple3);
			}
		}
	}	
}
