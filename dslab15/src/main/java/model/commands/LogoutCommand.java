package model.commands;

import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;
import chatserver.Chatserver;
import chatserver.ChatserverException;

public class LogoutCommand extends ChatserverCommand {

	
	@Override
	public ServerResponse execute() {
		Chatserver server = session.getServer();
		String user = session.getLoggedInUser();

		if(user == null) {
			return new ServerResponse("Not logged in.", false);
		} else {
			try {
				server.logout(user);
			} catch (ChatserverException e) {
				return new ServerResponse(e.getMessage(), false);
			}
			session.setLoggedInUser(null);
			session.close();
			
			return new ServerResponse("Successfully logged out", true);
		}
	}
}
