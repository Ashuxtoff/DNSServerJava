package answerMaker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.Stream;

import javax.xml.ws.handler.MessageContext.Scope;

import cache.DNSServerCache;
import tuples.Tuple;

public class DNSAnswerMaker {
	
	private DNSServerCache cache = null;
	private String name = "";
	private String type = "";
	private HashMap<String, Integer> namesDict = new HashMap<String, Integer>();
	
	private int getIntFrom(byte[] bytes, int index, int bitsCount) {
		int result = 0;
		for (int i = index + bitsCount - 1; i >= index; i--)
			result += consideredSignInt(bytes[i]) * (int)Math.pow(2, (index + bitsCount - 1 - i) * 8);
		return result;
	}
	
	private int consideredSignInt(byte b) {
		if (b >= 0)
			return (int)b;
		return (int)b + 256;
	}
	
	private String getStringFrom(byte[] bytes, int start, int end) {
		return new String(Arrays.copyOfRange(bytes, start, end));
	}
	
	private byte[] concateByteArrays(byte[][] arrays) {
		int totalLength = 0;
		for (byte[] array : arrays)
			totalLength += array.length;
		byte[] resultArray = new byte[totalLength];
		int index = 0;
		for (byte[] array : arrays) 
			for (byte b : array) {
				resultArray[index] = b;
				index ++;
			}
		return resultArray;
	}
	
	private Tuple<String, String> extractRequestData(byte[] request){
		ArrayList<String> labels = new ArrayList<String>();
		byte[] query = Arrays.copyOfRange(request, 12, request.length);
		int cursor = 0;
		int labelLength = getIntFrom(query, cursor, 1);
		while (labelLength != 0) {
			cursor ++;
			labels.add(getStringFrom(query, cursor, cursor + labelLength));
			cursor += labelLength;
			labelLength = getIntFrom(query, cursor, 1);
		}
		String name = String.join(".", labels);
		cursor += 2;
		String type = "";
		if (query[cursor] == 1)
			type = "A";
		else type = "NS";
		return new Tuple<String, String>(name, type);
	}
	
	public DNSAnswerMaker(String type, String target, DNSServerCache cache){
		this.cache = cache;
		this.name = target;
		this.type = type;
	}
	
	private byte[] makeHeader() {
		Random randomizer = new Random();
		int id = randomizer.nextInt(99);
		byte[] request_id = new byte[] {0, (byte)id};
		byte[] messageType = new byte[] {81, 0};
		byte[] questions = new byte[] {0, 1}; // тут не уверен, чекнуть еще раз, че это вообще
		int typeNameInfoLength = cache.getInfo(type + name).size();
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(typeNameInfoLength);
		byte[] answers = Arrays.copyOfRange(buffer.array(), 2, 4);
		byte[] authorityAndAdditional = new byte[] {0, 0, 0, 0};
		int resultArrayLength = request_id.length + messageType.length + questions.length + answers.length + authorityAndAdditional.length;
		byte[] result = new byte[resultArrayLength];
		int index = 0;
		byte[][] bytes = new byte[][] {request_id, messageType, questions, answers, authorityAndAdditional};
		return concateByteArrays(bytes);
	}
	
	private byte[] makeQuery() {
		int last = 12;
		this.namesDict.put(this.name, last);
		String[] splittedName = this.name.split("\\.");
		for (int i = 0; i < splittedName.length - 1; i++) {
			last += 1 + splittedName[i].length();
			namesDict.put(String.join(".", Arrays.copyOfRange(splittedName, i, splittedName.length)), last);
		}
		ArrayList<Byte> data = new ArrayList<Byte>();
		for (String label : splittedName) {
			data.add((byte)label.length()); //Нужно чтобы был один байт, а не четыре
			for (char c : label.toCharArray()) {
				data.add((byte)c);
			}
		}
		data.add((byte)0);
		data.add((byte)0);
		if (this.type.equals("A")) 
			data.add((byte)1);
		else if (this.type.equals("NS"))
			data.add((byte)2);
		else System.out.print("unrecognized type");
		data.add((byte)0);
		data.add((byte)1);
		byte[] result = new byte[data.size()];
		for (int i = 0; i < data.size(); i++) {
			result[i] = data.get(i);
		}
		return result;
	}
	
	private byte[] makeAnswers(int headerLength, int queryLength) {
		ArrayList<Byte> answers = new ArrayList<Byte>();
		ArrayList<String> requestedData = this.cache.getData(name, type);
		for (int i = 0; i < requestedData.size(); i++) {
			byte[] nameReference = new byte[] {(byte) 192, (byte)12};
			byte[] typeCode = new byte[2];
			typeCode[0] = (byte)0;
			if (this.type.equals("A")) 				
				typeCode[1] = (byte)1;
			else if (this.type.equals("NS"))
				typeCode[1] = (byte)2;
			else System.out.print("unrecognized type");
			byte[] classIn = new byte[] {(byte)0, (byte)1};
			int ttlSec = Math.round(cache.getTtl(type + name).get(i) / 1000);
			ByteBuffer buffer = ByteBuffer.allocate(4);
			buffer.putInt(ttlSec);
			byte[] ttl = buffer.array();
			byte[][] firstPartItems = new byte[][] {nameReference, typeCode, classIn, ttl};
			byte[] firstPart = concateByteArrays(firstPartItems);
			byte[] allByteData = new byte[0];
			byte[] secondPart = new byte[0];
			if (this.type.equals("A")) {
				byte[] dataLength = new byte[] {(byte)0, (byte)4};
				String[] splitted = this.cache.getData(this.name, "A").get(i).split("\\.");
				byte[] octets = new byte[4];
				int j = 0;
				for (String stringOctet : splitted) {
					int intOctet = Integer.parseInt(stringOctet);
					octets[j] = (byte)intOctet;
					j ++;
				}
				byte[][] arrays = new byte[][] {firstPart, dataLength, octets};
				allByteData = concateByteArrays(arrays);	
			}
			else if (this.type.equals("NS")) {
				ArrayList<Byte> secondPartList = new ArrayList<Byte>();
				ArrayList<String> gottenData = this.cache.getData(this.name, "NS");
				if (gottenData != null) {
					String[] splitted = gottenData.get(i).split("\\.");
					boolean finished = false;
					while (!finished && splitted.length > 0) {
						byte[] firstLabel = splitted[0].getBytes();
						secondPartList.add((byte)firstLabel.length);
						for (byte b : firstLabel)
							secondPartList.add(b);					
						String remaining = String.join(".", Arrays.copyOfRange(splitted, 1, splitted.length));
						if (this.namesDict.containsKey(remaining)) {
							finished = true;
							secondPartList.add((byte)192);
							secondPartList.add((byte)(int)this.namesDict.get(remaining));
						}
						else 
							this.namesDict.put(remaining, headerLength + queryLength + 2 + firstPart.length + secondPartList.size());
						splitted = Arrays.copyOfRange(splitted, 1, splitted.length);
					}
					secondPart = new byte[secondPartList.size()];
					int index = 0;
					for (byte b : secondPart) {
						secondPart[index] = b;
						index ++;
					}			
					buffer.clear();
					buffer.putInt(secondPart.length);
					byte[] array = buffer.array();
					byte[] secondPartLength = Arrays.copyOfRange(array, 2, 4);
					byte[][] items = new byte[][] {firstPart, secondPartLength, secondPart};
					allByteData = concateByteArrays(items);
				}
			}
			for (byte b : allByteData)
				answers.add(b);
		}
		byte[] result = new byte[answers.size()];
		int index = 0;
		for (byte b : answers) {
			result[index] = b;
			index ++;
		}
		return result;
	}
	
	public byte[] makePackage() {
		byte[] header = makeHeader();
		byte[] query = makeQuery();
		byte[] answers = makeAnswers(header.length, query.length);
		byte[][] allParts = new byte[][] {header, query, answers};
		return concateByteArrays(allParts);
	}
	
}
