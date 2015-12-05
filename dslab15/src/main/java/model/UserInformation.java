package model;

import channel.Channel;

public class UserInformation {
	private String username;
	private String password;
	private String ipAddress;
	
	public UserInformation(String username, String password) {
		super();
		this.username = username;
		this.password = password;
	}
	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}

	public String getAddress() {
		return ipAddress;
	}
	public void setAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
}
