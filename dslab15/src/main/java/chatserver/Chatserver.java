package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import model.UserInformation;
import model.responses.ServerMessage;
import channel.Channel;
import channel.ChannelException;
import channel.TcpChannel;
import cli.Command;
import cli.Shell;
import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	
	private Shell shell;
	
	// a map of users and their passwords
	private Map<String, UserInformation> userInformation = new ConcurrentHashMap<String, UserInformation>();
	
	// map of logged in (online) users and their connection handlers (UserSession)
	private Map<String, Channel> userChannels = new ConcurrentHashMap<String, Channel>();
	
	// 
	private List<UserSession> sessions = new ArrayList<UserSession>();
	
	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;
	private ExecutorService pool = Executors.newFixedThreadPool(10);

	boolean running = true;
	
	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Chatserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		
		// load user info
		Config userConfig = new Config("user");
		
		for(String key:userConfig.listKeys()){
			String username = key.substring(0, key.length()-9);
			UserInformation ui = new UserInformation(username, userConfig.getString(key));
			userInformation.put(username, ui);
		}
		
		// initialize shell
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		pool.execute(shell);
	}

	@Override
	public void run() {
		// create and start a new TCP ServerSocket
		try {
			serverSocket = new ServerSocket(config.getInt("tcp.port"));
			// handle incoming connections from client in a separate thread
			pool.execute(new ConnectionListener());
			
			datagramSocket = new DatagramSocket(config.getInt("udp.port"));
			pool.execute(new UdpListener());
		} catch (IOException e) {
			throw new RuntimeException("Cannot listen on TCP port.", e);
		}
	}

	@Override
	@Command
	public String users() throws IOException {
		String response = "";
		
		// create sorted list of user names
		List<String> names = new ArrayList<String>(userInformation.keySet());

		Collections.sort(names);
		
		if(userInformation.isEmpty()) {
			response = "No registered users found.";
		} else {
			for(String name : names){
				UserInformation ui = userInformation.get(name);
				response += ui.getUsername() + " " + (isLoggedIn(ui.getUsername()) ? "online" : "offline") + "\n";
			}
		}
		
		return response;
	}

	@Override
	@Command
	public String exit() throws IOException {
		running = false;
		pool.shutdownNow();
		serverSocket.close();
		datagramSocket.close();
		
		// close sessions to logged in clients
		for(Channel c:userChannels.values()){
			c.close();
		}
		
		// close connections to logged out clients
		for(UserSession s:sessions){
			s.close();
		}
		
		return "Good bye";
	}
	
	public boolean isLoggedIn(String username) {
		return userChannels.containsKey(username);
	}

	public void authenticate(String username, Channel userChannel){
		UserInformation ui = userInformation.get(username);
		userChannels.put(username, userChannel);
	}
	
	public void login(String username, String password, Channel userChannel) throws ChatserverException {
		UserInformation ui = userInformation.get(username);
		
		if(ui == null || !ui.getPassword().equals(password)) {
			throw new ChatserverException("Wrong username or password");
		} else {
			userChannels.put(username, userChannel);
		}
	}
	
	public void logout(String username) throws ChatserverException  {
		if(!isLoggedIn(username)) throw new ChatserverException("Not logged in");
		
		userInformation.get(username).setAddress(null);
		userChannels.remove(username);
	}
	
	public void send(String username, String message) throws ChatserverException  {
		if(!isLoggedIn(username)) throw new ChatserverException("Not logged in");
		boolean fail = false;
		
		for(String key:userChannels.keySet()) {
			if(!username.equals(key)){
				Channel c = userChannels.get(key);
				try {
					c.write(new ServerMessage(username + ": " + message));
				} catch (ChannelException e) {
					fail = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		if(fail) throw new ChatserverException("Message could not be delivered to all clients.");
	}
	
	public void register(String username, String address) throws ChatserverException {
		if(!isLoggedIn(username)) throw new ChatserverException("Not logged in");
		
		userInformation.get(username).setAddress(address);
	}
	
	public String lookup(String username) throws ChatserverException {
		String errorMessage = "No currently online user is registered with name " + username;
		
		if(!isLoggedIn(username)) throw new ChatserverException(errorMessage);
		
		String address = null;
		
		if(userInformation.containsKey(username)) {
			address = userInformation.get(username).getAddress();
		}
		
		if(address == null) throw new ChatserverException(errorMessage);
		
		return address;
	}
	
	public String list() {
		String response = "Online users: ";
		
		// create sorted list of user names
		List<String> names = new ArrayList<String>(userChannels.keySet());
		Collections.sort(names);
		
		for(String name : names) {
			response += "\n* " + name;
		}
		if(userChannels.isEmpty())
			response += "\nNo users online";
		
		return response;
	}
	
	
	
	private class ConnectionListener implements Runnable {

		@Override
		public void run() {
			while (!Thread.interrupted()) {
				Socket socket = null;
				try {
					// wait for Client to connect
					socket = serverSocket.accept();
					Channel channel = new TcpChannel(socket);
					
					UserSession session = new UserSession(Chatserver.this, channel);
					
					sessions.add(session);
					
					pool.execute(session);
				} catch (ChannelException ce) {
					if(!pool.isShutdown()){
						System.err
								.println("Error occurred while waiting for/communicating with client: "
										+ ce.getMessage());
					}
				} catch (IOException e) {
					if(!pool.isShutdown()){
						System.err
								.println("Error occurred while waiting for/communicating with client: "
										+ e.getMessage());
					}
				}
			}
		}
		
	}
	
	private class UdpListener implements Runnable {

		@Override
		public void run() {
			byte[] buffer;
			DatagramPacket packet;
			
			while (!Thread.interrupted()) {
				try {
					buffer = new byte[1024];

					packet = new DatagramPacket(buffer, buffer.length);

					datagramSocket.receive(packet);
					
					pool.execute(new DatagramPacketHandler(packet));
				} catch (IOException e) {
					if(!pool.isShutdown()){
						System.err
								.println("Error occurred while waiting for/handling packets: "
										+ e.getMessage());
					}
				}
			}
		}
	}
	
	private class DatagramPacketHandler implements Runnable {
		private DatagramPacket packet;
		
		private DatagramPacketHandler(DatagramPacket packet){
			this.packet = packet;
		}
		
		@Override
		public void run() {
			// get the data from the packet
			String request = new String(packet.getData());

			System.out.println("Received request-packet from client: "
					+ request);

			String response;
			if (request.startsWith("!list")) {
				response = list();
			} else {
				response = "Unknown command";
			}

			InetAddress address = packet.getAddress();
			int port = packet.getPort();
			byte[] buffer = response.getBytes();
			packet = new DatagramPacket(buffer, buffer.length, address,	port);
			try {
				datagramSocket.send(packet);
			} catch (IOException e) {

			}
		}
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0],
				new Config("chatserver"), System.in, System.out);
		
		new Thread(chatserver).run();
	}

}
