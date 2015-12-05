package model.commands;

import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;
import chatserver.Chatserver;
import chatserver.ChatserverException;

public class LookupCommand extends ChatserverCommand {
	private String lookupName;

	public LookupCommand(String lookupName) {
		this.lookupName = lookupName;
	}

	@Override
	public ServerResponse execute() {
		Chatserver server = session.getServer();
		String user = session.getLoggedInUser();

		if (user == null) {

			return new ServerResponse("Not logged in.", false);
		} else {
			try {
				return new ServerResponse(server.lookup(lookupName), true);
			} catch (ChatserverException e) {
				return new ServerResponse(e.getMessage(), false);
			}
		}
	}

}
