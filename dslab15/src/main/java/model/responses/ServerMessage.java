package model.responses;

import java.io.Serializable;

public class ServerMessage implements Serializable {
	private String message;

	public ServerMessage(String message) {
		super();
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
	
	
}
