package server;

import java.net.ServerSocket;
import java.net.Socket;
import DNSServer.DNSServer;
import threadDispatcher.ThreadDispatcher;
import webServer.ClientWorker;

public class Server {
	
	private static int port = 1112;
	
	public static void main(String[] args) {
		ThreadDispatcher dispatcher = ThreadDispatcher.instance;
		DNSServer dnsServer = new DNSServer();
		try (ServerSocket server = new ServerSocket(port)){
			while (!server.isClosed()) {			
				Socket client = server.accept();
				dispatcher.add(new DNSClientWorker(client, dnsServer));
			}
		}
		catch (Exception e) {
			System.out.print(e.getMessage());
		}	
	}
}
