package model.commands;

import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;
import chatserver.Chatserver;
import chatserver.ChatserverException;

public class RegisterCommand extends ChatserverCommand {
	private String address;
	
	public RegisterCommand(String address) {
		this.address = address;
	}

	@Override
	public ServerResponse execute() {
		Chatserver server = session.getServer();
		String user = session.getLoggedInUser();

		if (user == null) {
			return new ServerResponse("Not logged in.", false);
		} else {
			try {
				server.register(user, address);
				return new ServerResponse("Successfully registered address for " + user + ".", true);
			} catch (ChatserverException e) {
				return new ServerResponse(e.getMessage(), false);
			}

		}

	}

}
