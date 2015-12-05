package model.commands;

import java.io.Serializable;

import model.responses.ServerResponse;
import chatserver.Chatserver;
import chatserver.UserSession;

/**
 * Responsibilities:
 * authorization
 * sending a response to requester
 * 
 * @author Stefan
 */
public abstract class ChatserverCommand implements Serializable {
	protected UserSession session;
	
	/**
	 * Precondition: The user session must've been set with setUserSession
	 *  
	 * @return information about the commands outcome
	 */
	public abstract ServerResponse execute();

	public void setUserSession(UserSession session) {
		this.session = session;
	}
}
