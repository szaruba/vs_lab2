package client;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hash.HashService;
import channel.*;
import model.commands.*;
import model.responses.ServerMessage;
import model.responses.ServerResponse;
import cli.Command;
import cli.Shell;
import org.bouncycastle.util.encoders.Base64;
import util.*;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;

	private Shell shell;
	private ExecutorService pool = Executors.newCachedThreadPool();
	
	private ChannelDecorator channel;
	
	private DatagramSocket datagramSocket;
	
	private String lastReceivedPublicMessage = null;
	
	private ServerMessageReader smr;
	private PrivateMessageReader pmr;
	
	private String username;
	private Key macKey;

	private PublicKey controllerPublicKey;

	private Key serverKey;

	private boolean authenticated;

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
		authenticated = false;

		// init shell
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		pool.execute(shell);
		
		// connect to server
		try {
			SecurityUtils.registerBouncyCastle();
			//loadControllerPublicKey();
			//Socket socket = new Socket(config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
			
			datagramSocket = new DatagramSocket();


			
		} catch (IOException i) {
			System.out.println("Could not connect to server");
			System.exit(1);
		}
	}

	private void openChannel(){
		try {
			Socket socket = new Socket(config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
			Channel tcpChannel = new TcpChannel(socket);
			this.channel = new Base64Channel(tcpChannel);

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ChannelException e) {
			e.printStackTrace();
		}
	}

	private void startServerMessageReader(){
		smr = new ServerMessageReader();
		pool.execute(smr);
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
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}


	private void openAESChannel(byte[] msg, byte[] aesKey, byte[] aesIv){
		try {
			AESChannel aesChannel = new AESChannel(this.channel, aesKey, aesIv);


			String thirdMessage = new String(msg, Charset.defaultCharset());

			final String B64 = "a -zA -Z0 -9/+ " ;
			assert thirdMessage.matches("["+B64+"]{43}=") : "3rd message ";

			if(thirdMessage.matches("["+B64+"]{43}=")){
				System.out.println("3. Nachricht OK");
				aesChannel.write(msg);
				aesChannel.setActive(true);
				System.out.println("Benutzer " + this.username + "wurde erfolgreicht authentifiziert.");
				this.channel = aesChannel;
			}else {
				System.out.print("3. Nachricht FALSCH");
			}


		} catch (ChannelException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	@Command
	public String authenticate(String username) throws IOException {
		this.username = username;

		//Sende 1. Nachricht
		byte[] challenge = SecurityUtils.generateEncryptedSecureRandom();
		byte[] encryptedMsg = null;

		String msg = "!authenticate " + username + " " + new String(challenge, Charset.defaultCharset());
		//System.out.println(msg);
		String publicKeyPath = config.getString("chatserver.key");
		PublicKey publicKey = Keys.readPublicPEM(new File(publicKeyPath));

		//String privateKeyPath = new Config("chatserver").getString("key");
		//PrivateKey privateKey = Keys.readPrivatePEM(new File(privateKeyPath));

		final String B64 = "a -zA -Z0 -9/+ " ;
		assert msg.matches ("!authenticate [\\w\\.]+ ["+B64+"]{43}=") : " 1st message ";

		if(msg.matches ("!authenticate [\\w\\.]+ ["+B64+"]{43}=")){
			System.out.println("1. Nachricht OK");
		}else{
			System.out.println("1. Nachricht FALSCH");
		}

		if(authenticated == false){
			System.out.println("Öffnen des Channels");
			openChannel();
		}

		try{
			Cipher cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
			encryptedMsg = cipher.doFinal(msg.getBytes());

			System.out.println("1. Nachricht:" + encryptedMsg);
			channel.write(encryptedMsg);

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (ChannelException e) {
			e.printStackTrace();
		}
		//--------------------------------------------------------------
		//Empfange Nachricht vom Server
		try {
			byte[] okMsg = (byte[]) channel.read();
			//System.out.println("2. empfangene Nachricht " + okMsg);
			String decryptedMessage;
			String privateKeyPathForOk = config.getString("keys.dir");
			PrivateKey privateKeyForOk = Keys.readPrivatePEM(new File(privateKeyPathForOk+"/"+this.username+".pem"));
			Cipher cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(Cipher.DECRYPT_MODE, privateKeyForOk);
			decryptedMessage = new String(cipher.doFinal(okMsg), Charset.defaultCharset());

			assert decryptedMessage.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}==") : " 2nd message ";

			if(decryptedMessage.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}==")){
				System.out.println("2. Nachricht OK");
			}else{
				System.out.println("2. Nachricht FALSCH ");
			}

			System.out.println("2. Nachricht entschlüsselt" + decryptedMessage);

			String[] parts = decryptedMessage.split("\\s");
			if(parts.length != 5){
				System.out.println("Error in recieving ok message from server");
				return null;
			}
			if(!parts[1].equals(new String(challenge, Charset.defaultCharset()))){
				System.out.println("sent challenge: "+parts[1]);
				System.out.println("sent challenge: "+new String(challenge,Charset.defaultCharset()));
				System.out.println("ERROR: Handshake failed due to client challenge mismatch!");
				return null;
			}else {
				openAESChannel(parts[2].getBytes(), parts[3].getBytes(), parts[4].getBytes());
				authenticated = true;
				startServerMessageReader();
			}

		} catch (ChannelException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		}

		return null;
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
		if(authenticated == false){
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
		}else{
			return "You have to authenticate before you can logout!";
		}

	}

	@Override
	@Command
	public String send(String message) throws IOException {
		if(authenticated == false){
			return executeCommand(new SendCommand(message));
		}else {
			return "You have to authenticate before you can send a message";
		}
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
		if(authenticated == false) {
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

				if (!isCorrect) {
					System.out.println("Response from " + username + " was tampered!");
				}
				if (output.contains("!tampered")) {
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
		}else {
			return "You have to authenticate before you send a private message";
		}
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		return executeCommand(new LookupCommand(username));
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if(authenticated == false) {
			try {
				channel.write(new RegisterCommand(privateAddress));
			} catch (ChannelException e) {
				return "Channel Error: " + e.getMessage();
			}
			ServerResponse response;
			try {
				response = smr.waitForServerResponse();

				// if successful open Socket to listen for private messages
				if (response.getSuccess()) {
					// stop current private message reader if active
					if (pmr != null) {
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
		}else{
			return "You have to authenticate before you can register.";
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
			} catch (IOException e) {
				e.printStackTrace();
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




}
