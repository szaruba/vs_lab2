package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.Key;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hash.HashService;
import model.commands.ChatserverCommand;
import model.commands.LoginCommand;
import model.commands.LogoutCommand;
import model.commands.LookupCommand;
import model.commands.RegisterCommand;
import model.commands.SendCommand;
import model.responses.ServerMessage;
import model.responses.ServerResponse;
import channel.Channel;
import channel.ChannelException;
import channel.TcpChannel;
import cli.Command;
import cli.Shell;
import util.Config;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;

	private Shell shell;
	private ExecutorService pool = Executors.newCachedThreadPool();
	
	private TcpChannel channel;
	
	private DatagramSocket datagramSocket;
	
	private String lastReceivedPublicMessage = null;
	
	private ServerMessageReader smr;
	private PrivateMessageReader pmr;
	
	private String username;
	private Key macKey;
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
	public Client(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;

		// init shell
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		pool.execute(shell);
		
		// connect to server
		try {
			Socket socket = new Socket(config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
			
			channel = new TcpChannel(socket);
			
			datagramSocket = new DatagramSocket();
			
			smr = new ServerMessageReader();
			pool.execute(smr);
			
		} catch (IOException i) {
			System.out.println("Could not connect to server");
			System.exit(1);
		} catch (ChannelException e) {
			System.out.println("Could not connect to server");
			System.exit(1);
		}
	}

	@Override
	public void run() {
		
	}

	private String executeCommand(ChatserverCommand c) {
		try {
			channel.write(c);
			
			ServerResponse response = smr.waitForServerResponse();
			
			return response.getMessage();
		} catch (ChannelException e) {
			return "Network error: " + e.getMessage();
		} catch (InterruptedException e) {
			return "No response was sent for that command";
		}
	}
	
	@Override
	@Command
	public String login(String username, String password) throws IOException {
		ChatserverCommand c = new LoginCommand(username, password);
		
		try {
			channel.write(c);
			
			ServerResponse response = smr.waitForServerResponse();
			
			if(response.getSuccess()) {
				this.username = username;
			}
			
			return response.getMessage();
		} catch (ChannelException e) {
			return "Network error: " + e.getMessage();
		} catch (InterruptedException e) {
			return "No response was sent for that command";
		}
	}

	@Override
	@Command
	public String logout() throws IOException {
		ChatserverCommand c = new LogoutCommand();
		
		try {
			channel.write(c);
			
			ServerResponse response = smr.waitForServerResponse();
			
			if(response.getSuccess()) {
				this.username = null;
			}
			
			return response.getMessage();
		} catch (ChannelException e) {
			return "Network error: " + e.getMessage();
		} catch (InterruptedException e) {
			return "No response was sent for that command";
		}
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		return executeCommand(new SendCommand(message));
	}

	@Override
	@Command
	public String list() throws IOException {
		byte[] buffer = "!list".getBytes();
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(config.getString("chatserver.host")),
				config.getInt("chatserver.udp.port"));
		datagramSocket.send(packet);

		buffer = new byte[1024];
		DatagramPacket response = new DatagramPacket(buffer, buffer.length);
		datagramSocket.receive(response);
		
		return new String(response.getData());
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {
		String address = lookup(username);

		String ip = address.split(":")[0];
		int port = Integer.parseInt(address.split(":")[1]);

		Socket s = new Socket(ip, port);

		String response = null;

		String hashedMessage = HashService.hashMessage(macKey, message);
		try {
			Channel privateChannel = new TcpChannel(s);

			privateChannel.write(hashedMessage + " " + this.username + " (private): " + message);

			String output = privateChannel.read().toString();
			String outputHashedMessage = output.substring(0, output.indexOf(" "));
			String outputMessage = output.split("\\s")[2];

			Boolean isCorrect = HashService.isHashedCorrectly(macKey, outputHashedMessage, outputMessage);

			if (!isCorrect){
				System.out.println("Response from " + username + " was tampered!");
			}
			if (output.contains("!tampered")){
				response = username + " replied with " + outputMessage + " (tampered!)";
			} else {
				response = username + " replied with " + outputMessage;
			}

		} catch (ChannelException e) {
			response = "Network error occurred";
		} finally {
			s.close();
		}

		return response;
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		return executeCommand(new LookupCommand(username));
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		try {
			channel.write(new RegisterCommand(privateAddress));
		} catch (ChannelException e) {
			return "Channel Error: " + e.getMessage();
		}
		ServerResponse response;
		try {
			response = smr.waitForServerResponse();
			
			// if successful open Socket to listen for private messages
			if(response.getSuccess()) {
				// stop current private message reader if active
				if(pmr != null) {
					pmr.stop();
				}
				
				int port = Integer.parseInt(privateAddress.split(":")[1]);
				pmr = new PrivateMessageReader(new ServerSocket(port));
				pool.execute(pmr);
			}
			
			return response.getMessage();
		} catch (InterruptedException e) {
			return "No response was sent for that command";
		}
	}
	
	@Override
	@Command
	public String lastMsg() throws IOException {
		if(lastReceivedPublicMessage == null)
			return "No message received!";
		else
			return lastReceivedPublicMessage;
	}

	@Override
	@Command
	public String exit() throws IOException {
		if(username != null) {
			try {
				// logout
				channel.write(new LogoutCommand());
				smr.waitForServerResponse();
			} catch (ChannelException e) {
				
			} catch (InterruptedException e) {
				
			}
		}
		
		pool.shutdownNow();
		
		// stop resources
		if(pmr != null)
			pmr.stop();
		
		smr.stop();
	
		return "Bye!";
	}

	private class PrivateMessageReader implements Runnable {
		private boolean running = true;
		private ServerSocket serverSocket;
		private Socket socket;

		public PrivateMessageReader(ServerSocket serverSocket) {
			this.serverSocket = serverSocket;
		}

		@Override
		public void run() {
			while(running) {
				try{
					socket = serverSocket.accept();
					Channel channel = new TcpChannel(socket);
					String message = channel.read().toString();

					String hashedMessage = message.substring(0, message.indexOf(" "));
					String onlyMessage = message.split("\\s")[3];
					Boolean isCorrect = HashService.isHashedCorrectly(macKey, hashedMessage, onlyMessage);
					String printMessage = message.substring(message.indexOf(" ") + 1, message.length());

					if (isCorrect){
						String hashedAnswer = HashService.hashMessage(macKey, "!ack");
						channel.write(hashedAnswer + " !ok !ack");
						System.out.println(printMessage);
					} else {
						String hashedAnswer = HashService.hashMessage(macKey, "!ack");
						channel.write(hashedAnswer + " !tampered !ack");
						System.out.println("The following message was tampered!");
						System.out.println(printMessage);
					}

					socket.close();
				} catch (ChannelException e) {
					System.out.println("Private message error: " + e.getMessage());
				} catch (IOException e) {
					System.out.println("Private message error: " + e.getMessage());
				}
			}
		}

		public void stop() {
			running = false;
			try {
				serverSocket.close();
				socket.close();
			} catch (IOException e) {

			}
		}
	}
	
	private class ServerMessageReader implements Runnable {
		private boolean running = true;
		private ServerResponse lastServerResponse = null;
		
		@Override
		public void run() {
			try {
				while(running) {
					
						ServerMessage serverMessage = (ServerMessage) channel.read();
						
						if(serverMessage instanceof ServerResponse) {
							// inform waiting thread that a response has arrived
							lastServerResponse = (ServerResponse) serverMessage;
							synchronized(this){
								notify();
							}
						} else {
							lastReceivedPublicMessage = serverMessage.getMessage();
							
							//write public messages to console
							try {
								shell.writeLine(serverMessage.getMessage());
							} catch (IOException e) {
								
							}
						}
					
				}
			} catch (ChannelException e) {
				System.out.println("Server not reachable.");
			}
		}
		
		public synchronized ServerResponse waitForServerResponse() throws InterruptedException {
			wait();
			return lastServerResponse;
		}
		
		public void stop(){
			running = false;
			channel.close();
		}
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in,
				System.out);
		
		new Thread(client).start();
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
