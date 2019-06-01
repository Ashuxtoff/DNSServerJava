package answerParser;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TooManyListenersException;

import answerItem.AnswerItem;
import tuples.Tuple3;

public class DNSAnswerParser {
	
	private byte[] pack;
	private ArrayList<AnswerItem> answer = new ArrayList<AnswerItem>();
	private ArrayList<AnswerItem> additional = new ArrayList<AnswerItem>();
	private ArrayList<AnswerItem> authority = new ArrayList<AnswerItem>();
	
	private int consideredSignInt(byte b) {
		if (b >= 0)
			return (int)b;
		return (int)b + 256;
	}
	
	private int getIntFrom(byte[] bytes, int index, int bitsCount) {
		int result = 0;
		for (int i = index + bitsCount - 1; i >= index; i--)
			result += consideredSignInt(bytes[i]) * (int)Math.pow(2, (index + bitsCount - 1 - i) * 8);
		return result;
	}
	
	private String getStringFrom(byte[] bytes, int start, int end) {
		return new String(Arrays.copyOfRange(bytes, start, end));
	}
	
	private String detectType() {
		byte[] query = Arrays.copyOfRange(pack, 12, pack.length);
		String queryString = Arrays.toString(query);
		int prev = queryString.indexOf('0') + 1;
		int code = getIntFrom(query, prev + 1, 2);
		if (code == 1) return "A";
		else if (code == 2) return "NS";
		else return "Unrecognized Type";
	}
	
	private String detectName() {
		ArrayList<String> labels = new ArrayList<String>();
		byte[] query = Arrays.copyOfRange(pack, 12, pack.length);
		int cursor = 0;
		int labelLength = consideredSignInt(query[cursor]);
		while (labelLength != 0) {
			cursor++;
			labels.add(getStringFrom(query, cursor, cursor + labelLength));
			cursor += labelLength;		
			labelLength = consideredSignInt(query[cursor]);
		}
		return String.join(".", labels);
	}
	
	private ArrayList<String> readRecursiveLabels(byte[] bytes, int cursor, ArrayList<String> result) {
		if (consideredSignInt(bytes[cursor]) >= 192) {
			cursor = getIntFrom(bytes, cursor, 2) - 3 * (int)Math.pow(2, 14);
			int labelLength = consideredSignInt(pack[cursor]);
			while (labelLength != 0 && labelLength < 63) {
				cursor ++;
				String label = getStringFrom(pack, cursor, cursor + labelLength);
				result.add(label);
				cursor += labelLength;
				labelLength = consideredSignInt(pack[cursor]);
			}
			readRecursiveLabels(pack, cursor, result);
		}
		return result;
	}

	public DNSAnswerParser(byte[] pack) {
		this.pack = pack;
	}
	
	public Tuple3<HashMap<String, ArrayList<AnswerItem>>, String, String> extractData(){
		int answersCount = getIntFrom(pack, 6, 2);
		int authorityCount = getIntFrom(pack, 8, 2);
		int additionalCount = getIntFrom(pack, 10, 2);
		int readTokensCount = 0;
		byte[] question = Arrays.copyOfRange(pack, 12, pack.length);
		byte[] answer = Arrays.copyOfRange(question, Arrays.toString(question).indexOf('0') + 6, question.length);
		int cursor = 0;
		while (readTokensCount < answersCount + authorityCount + additionalCount) {
			String data = "";
			cursor += 6;
			int ttl = getIntFrom(answer, cursor, 4);
			cursor += 4;
			int dataLength = getIntFrom(answer, cursor, 2);
			cursor += 2;
			if (dataLength == 4) {
				String[] octets = new String[4];
				for (int i = 0; i < dataLength; i++) 
					octets[i] = String.valueOf(consideredSignInt(answer[cursor + i]));
				data = String.join(".", octets);
				cursor += 4;
			}
			else {
				int nextLength = answer[cursor];
				while (nextLength < 63) {
					cursor ++;
					data += getStringFrom(answer, cursor, cursor + nextLength) + ".";
					cursor += nextLength;
					nextLength = consideredSignInt(answer[cursor]);
				}
				ArrayList<String> labels = readRecursiveLabels(answer, cursor, new ArrayList<String>());
				cursor += 2;
				data += String.join(".", labels.toArray(new String[labels.size()]));
			}
			if (readTokensCount >= answersCount + authorityCount)
				additional.add(new AnswerItem(data, ttl));
			else if (readTokensCount >= answersCount)
				authority.add(new AnswerItem(data, ttl));
			else
				this.answer.add(new AnswerItem(data, ttl));
			readTokensCount += 1;
		}
		HashMap<String, ArrayList<AnswerItem>> result = new HashMap<String, ArrayList<AnswerItem>>();
		result.put("answer", this.answer);
		if (this.authority.size() > 0)
			result.put("authority", this.authority);
		if (this.additional.size() > 0)
			result.put("additional", this.additional);
		return new Tuple3<HashMap<String,ArrayList<AnswerItem>>, String, String>(
			result, detectType(), detectName());
	}
	
}
