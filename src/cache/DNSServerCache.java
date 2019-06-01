package cache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.prefs.BackingStoreException;

import answerItem.AnswerItem;
import tuples.Tuple3;

public class DNSServerCache {
	
	private File backup;
	private long lastSeenTime = System.currentTimeMillis();
	private HashMap<String, ArrayList<String>> database = new HashMap<String, ArrayList<String>>();
	private HashMap<String, ArrayList<Long>> ttls =new HashMap<String, ArrayList<Long>>();
	
	public ArrayList<String> getInfo(String key) {
		return this.database.get(key);		
	}
	
	public ArrayList<Long> getTtl(String key) {
		return this.ttls.get(key);
	}
	
	public DNSServerCache(File backupFile) {
		this.backup = backupFile;
		boolean firstString = true;
		long rewriteTime = 0;
		try {
			BufferedReader reader = new BufferedReader(new FileReader(backupFile));
			String line = reader.readLine();
			while (line != null) {
				if (firstString) {
					rewriteTime = Long.parseLong(line);
					firstString = false;
				}
				else {
					String[] splittedLine = line.split("\\s+");
					String key = splittedLine[0];
					splittedLine = Arrays.copyOfRange(splittedLine, 1, splittedLine.length);
					if (splittedLine.length > 0) {
						this.database.put(key, new ArrayList<String>());
						this.ttls.put(key, new ArrayList<Long>());
						for (int i = 0; i < splittedLine.length; i++) {
							if (i % 2 == 0)
								this.database.get(key).add(splittedLine[i]);
							else
								this.ttls.get(key).add(
									Long.parseLong(splittedLine[i]) - (System.currentTimeMillis() - rewriteTime));
						} // в кеш кладется разница между временем в бэкапе и временем, которое прошло
					}
				}
				line = reader.readLine();
			}
			deleteOverdueData();			
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void fixData() {
		this.updateTtl();
		this.deleteOverdueData();
		this.writeBackup();
	}
	
	private void updateTtl() {
		long now = System.currentTimeMillis();
		for (String key : this.ttls.keySet())
			for (int i = 0; i < this.ttls.get(key).size(); i++)
				this.ttls.get(key).set(i, this.ttls.get(key).get(i) - now + this.lastSeenTime);
		this.lastSeenTime = now;
	}
	
	private void writeBackup() {
		try {
			FileWriter writer = new FileWriter(this.backup);
			writer.write(String.valueOf(System.currentTimeMillis()));
			for (String key : this.database.keySet()) {
				writer.write("\r\n" + key);
				for (int i = 0; i < this.database.get(key).size(); i++)
					writer.write("   " + this.database.get(key).get(i) + "   " + String.valueOf(this.ttls.get(key).get(i)));
			}
			writer.flush();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void deleteOverdueData() {
		ArrayList<Integer> removingIndexes = new ArrayList<Integer>();
		ArrayList<String> removingKeys = new ArrayList<String>();
		for (String key : this.ttls.keySet()) {
			for (int i = 0; i < this.ttls.get(key).size(); i++) 
				if (this.ttls.get(key).get(i) < 0) // тут чета странное было
					removingIndexes.add(i);			
//			Collections.reverse(removingIndexes);
			ArrayList<String> newItems = new ArrayList<String>();
			ArrayList<Long> newTtls = new ArrayList<Long>();
			for (int i = 0; i < this.database.get(key).size(); i++) {
				if (!removingIndexes.contains(i)) {
					newItems.add(this.database.get(key).get(i));
					newTtls.add(this.ttls.get(key).get(i));
				}
			}
			this.database.put(key, newItems);
			this.ttls.put(key, newTtls);
//				this.database.get(key).remove(index);
//				this.ttls.get(key).remove(index);
			removingIndexes.clear();
			if (this.ttls.get(key).size() == 0)
				removingKeys.add(key);
		}
		for (String key : removingKeys) {
			this.database.remove(key);
			this.ttls.remove(key);
		}
	}
	
	public void addData(Tuple3<HashMap<String, ArrayList<AnswerItem>>, String, String> data) {
		HashMap<String, ArrayList<AnswerItem>> answerItems = data.value1;
		String type = data.value2;
		String name = data.value3;
		ArrayList<String> answersData = new ArrayList<String>();
		ArrayList<Long> answerTtls = new ArrayList<Long>();
		for (AnswerItem item : answerItems.get("answer")) {
			answersData.add(item.getData());
			answerTtls.add(item.getTtl() * 1000);
		}
		this.database.put(type + name, answersData);
		this.ttls.put(type + name, answerTtls);
		if (answerItems.containsKey("authority")) {
			ArrayList<String> authorityData = new ArrayList<String>();
			ArrayList<Long> authorityTtls = new ArrayList<Long>();
			ArrayList<String> additionalData = new ArrayList<String>();
			for (AnswerItem item : answerItems.get("authority")) {
				authorityData.add(item.getData());
				authorityTtls.add(item.getTtl() * 1000);
			}
			if (answerItems.containsKey("additional")) 
				for (AnswerItem item : answerItems.get("additional"))
					additionalData.add(item.getData());
//			ArrayList<String> authDataList = new ArrayList<String>();
//			ArrayList<Long>  authTtlList = new ArrayList<Long>();
			for (int i = 0; i < additionalData.size(); i++) {
				String[] item = new String[] {additionalData.get(i)};
				Long[] ttl = new Long[] {authorityTtls.get(i)};
				this.database.put("A" + authorityData.get(i), new ArrayList<String>(Arrays.asList(item)));
				this.ttls.put("A" + authorityData.get(i), new ArrayList<Long>(Arrays.asList(ttl)));					
			}
			this.database.put("NS" + name, (ArrayList<String>)authorityData.clone());
			this.ttls.put("NS" + name, (ArrayList<Long>)authorityTtls.clone());
		}
		fixData();
	}
	
	public ArrayList<String> getData(String name, String type){
		fixData();
		String key = type + name;
		if (this.database.containsKey(key))
			return this.database.get(key);
		return null;
	}
}



//HashMap<String, ArrayList<Integer>> removingIndexes = new HashMap<String, ArrayList<Integer>>();
//long now = System.currentTimeMillis();
//long timeDifference = now - this.lastSeenTime;
//for (String key : this.ttls.keySet()) {
//	removingIndexes.put(key, new ArrayList<Integer>());
//	for (int i = 0; i < this.ttls.get(key).size(); i++) {
//		this.ttls.get(key).set(i, this.ttls.get(key).get(i) - timeDifference);
//		if (this.ttls.get(key).get(i) <= 0)
//			removingIndexes.get(key).add(i);
//	}
//}
//for (String key : removingIndexes.keySet()) {
//	for (int i = removingIndexes.get(key).size() - 1; i > 0; i--) {
//		this.database.get(key).remove(i);
//		this.ttls.get(key).remove(i);
//	}
//	if (removingIndexes.get(key).size() == 0)
//		removingIndexes.remove(key);	
//}
//this.lastSeenTime = now;
//String key = type + name;