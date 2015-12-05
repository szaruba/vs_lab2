package model.commands;

import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;
import chatserver.Chatserver;
import chatserver.ChatserverException;

public class SendCommand extends ChatserverCommand {
	private String message;
	
	public SendCommand(String message) {
		this.message = message;
	}

	@Override
	public ServerResponse execute() {
		Chatserver server = session.getServer();
		String user = session.getLoggedInUser();


		if (user == null) {

			return new ServerResponse("Not logged in.", false);

		} else {
			try {
				server.send(user, message);
				return new ServerResponse("Successfully sent public message.", true);
			} catch (ChatserverException e) {
				return new ServerResponse(e.getMessage(), false);
			}
		}
	}

}
