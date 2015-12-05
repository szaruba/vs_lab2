package model.commands;

import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;
import chatserver.Chatserver;
import chatserver.ChatserverException;

public class LoginCommand extends ChatserverCommand {
	private String username;
	private String password;
	
	public LoginCommand(String username, String password) {
		this.username = username;
		this.password = password;
	}
	
	@Override
	public ServerResponse execute() {
		Chatserver server = session.getServer();
		Channel channel = session.getChannel();
		
		try {
			if(session.getLoggedInUser() != null) {
				return new ServerResponse("Logout first", false);
			} else {
				server.login(username, password, channel);
				
				session.setLoggedInUser(username);
				
				return new ServerResponse("Successfully logged in.", true);
			}
		} catch (ChatserverException e) {
			return new ServerResponse(e.getMessage(), false);
		}
	}
}
