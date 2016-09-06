package my;

import java.util.ArrayList;
import java.util.List;

public class DroolsPojo {
	private String name;
	private int number;
	private List<String> asd;
	
	public DroolsPojo(String name, int number) {
		super();
		this.name = name;
		this.number = number;
		this.asd = new ArrayList<String>();
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getNumber() {
		return number;
	}
	public void setNumber(int number) {
		this.number = number;
	};
	public List<String> getAsd() {
	    if (this.asd == null) {
	        this.asd = new ArrayList<String>();
	    }
	    return this.asd;
	}
}
