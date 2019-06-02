package main;

import DNSServer.DNSServer;
import client.Client;
import server.Server;

public class Main {

	public static void main(String[] args) {
		Client client1 = new Client();
		Client client2 = new Client();
		Client client3 = new Client();
		String resutlt1 = client1.get("A", "e1.ru");
		try {
			Thread.sleep(2000);
		} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		String result2 = client2.get("NS", "e1.ru");
		String result3 = client3.get("A", "ns1.e1.ru");
		System.out.print(resutlt1);
		System.out.print("\r\n");
		System.out.print(result2);
		System.out.print("\r\n");
		System.out.print(result3);
	}

}
