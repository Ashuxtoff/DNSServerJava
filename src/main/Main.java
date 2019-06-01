package main;

import DNSServer.DNSServer;

public class Main {

	public static void main(String[] args) {
		DNSServer server = new DNSServer();
		server.run();
	}

}
