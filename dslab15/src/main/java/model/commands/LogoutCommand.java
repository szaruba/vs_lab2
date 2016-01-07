package model.commands;

import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;
import chatserver.Chatserver;
import chatserver.ChatserverException;

import java.io.IOException;

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
			Channel c = session.getChannel();

			try {
				c.write(new ServerResponse("Successfully logged out", true));
			} catch (ChannelException e) {

			} catch (IOException e) {

			}

			session.close();

			return null;
		}
	}
}
