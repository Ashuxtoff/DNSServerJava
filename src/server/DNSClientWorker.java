package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.Arrays;

import DNSServer.DNSServer;
import mainDeserializer.Deserializer;
import mainSerializer.Serializer;
import observingInterfaces.Observer;
import threaded.Threaded;

public class DNSClientWorker extends Threaded {
	
	private int clientPort = 1111;
	private Socket clientSocket; // ��� �����, ������� ����� ������ �� �������  // �����, ���������� ������ �������
	private DataInputStream serverInput;  // ����� ��������� ������  // ���, � ����� �� �� ������ �������� � ������ ������ ������? �� ������� ������ ��� �����, ������� �� ���� ��������, � � ���� ������ ������ ���������� �������?
	private DataOutputStream serverOutput;  // ������ ����������� ������ ������
	private DNSServer dnsServer;
	private Serializer serializer;
	private Deserializer<String> deserializer;
	
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

	public DNSClientWorker(Socket socket, DNSServer dnsServer) { // ������ ����� �����
		this.clientSocket = socket; // �� ������� ��� �����, ������ ������������ �� ������.
		// ������ ��������� ������� � ���� �������, ������ �� ���� ������, ���������, �� �������� �� � ���� ������ ������
		// ���� ��� ���������� �� ���������, ���� ��� - ���������� ����� ������ �������
		// ��� ������������ �����, ����� ������� ������� � �����������.	
		this.dnsServer = dnsServer;
		this.serializer = new Serializer();
		this.deserializer = new Deserializer<String>();
		try {
			this.serverInput = new DataInputStream(clientSocket.getInputStream());
			this.serverOutput = new DataOutputStream(clientSocket.getOutputStream());
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void execute() {
		byte[] requestBuffer = new byte[100];
		try {
			this.serverInput.read(requestBuffer);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		requestBuffer = cutBuffer(requestBuffer);
		String request = deserializer.deserialize(requestBuffer);
		String[] splitted = request.split(" ");
		String type = splitted[0];
		String target = splitted[1];
		String result = "";
		synchronized (this.dnsServer) {
			result = dnsServer.run(type, target);
		}
		byte[] answer = this.serializer.serialize(result);
		try {
			this.serverOutput.write(answer);
			this.serverOutput.flush();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		try {
			this.serverInput.close();
			this.serverOutput.close();
			this.clientSocket.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
	}


	public void notifyObserver(Observer observer) {
		observer.handleEvent(Thread.currentThread().getId(), Thread.currentThread().getName());			
	}
}
