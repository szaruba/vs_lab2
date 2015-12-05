package model.responses;

public class ServerResponse extends ServerMessage {

	private boolean success;
	
	public ServerResponse(String message, boolean success) {
		super(message);
		this.success = success;
	}
	
	public boolean getSuccess(){
		return success;
	}
}
