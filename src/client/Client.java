package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.Arrays;

import mainDeserializer.Deserializer;
import mainSerializer.Serializer;

public class Client {
	
	private int clientPort = 1111;
	private int serverPort = 1112;
	private Socket serverSocket;
	private Serializer serializer;
	private Deserializer<String> deserializer;
//	private DatagramSocket fromServerSocket;
	private DataOutputStream serverOutput;
	private DataInputStream serverInput;
	
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
	
	public Client() {
		this.serializer = new Serializer();
		this.deserializer = new Deserializer<String>();
		try {
//			this.fromServerSocket = new DatagramSocket(serverPort);
			this.serverSocket = new Socket("localhost", 1112); // подключаем к серверу
			this.serverOutput = new DataOutputStream(serverSocket.getOutputStream());
			this.serverInput = new DataInputStream(serverSocket.getInputStream());
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String get(String type, String target) {
		String request = type + " " + target; // собираем запрос
		byte[] serializedRequest = serializer.serialize(request);
		try {
			this.serverOutput.write(serializedRequest);
			this.serverOutput.flush();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		byte[] responseBuffer = new byte[100];
		try {
			this.serverInput.read(responseBuffer); // читаем ответ
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}
		responseBuffer = cutBuffer(responseBuffer);
		String response = this.deserializer.deserialize(responseBuffer);
		try {
			this.serverInput.close();
			this.serverOutput.close();
			this.serverSocket.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}		
		return response;
	}
}
