package answerItem;

public class AnswerItem {
	
	private String data;
	private long ttl;

	public AnswerItem(String data, long ttl) {
		this.data = data;
		this.ttl = ttl;
	}
	
	public String getData() {
		return data;
	}
	
	public long getTtl() {
		return ttl;
	}
}
