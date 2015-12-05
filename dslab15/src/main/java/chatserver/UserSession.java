package chatserver;

import java.io.Closeable;
import java.io.IOException;

import model.commands.ChatserverCommand;
import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;

public class UserSession implements Runnable {
	private Chatserver server;
	private Channel channel;
	private String loggedInUser;
	private boolean running = true;
	
	public UserSession(Chatserver server, Channel channel) {
		this.channel = channel;
		this.server = server;
		loggedInUser = null;
	}

	@Override
	public void run() {
		Object request;
		// read client requests
		
		while (running) {

			try {
				request = channel.read();
				
				ChatserverCommand command = (ChatserverCommand) request;
				
				command.setUserSession(this);
				
				channel.write(command.execute());
				
			} catch (ChannelException e) {
					
			}
			
		}
	}

	public String getLoggedInUser() {
		return loggedInUser;
	}

	public void setLoggedInUser(String loggedInUser) {
		this.loggedInUser = loggedInUser;
	}

	public Chatserver getServer() {
		return server;
	}

	public Channel getChannel() {
		return channel;
	}
	
	public void close() {
		running = false;
		channel.close();
	}	
}