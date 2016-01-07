package chatserver;


import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.*;

import channel.AESChannel;
import channel.Base64Channel;
import model.commands.ChatserverCommand;

import channel.Channel;
import channel.ChannelException;
import org.bouncycastle.util.encoders.Base64;
import util.Config;
import util.Keys;
import util.SecurityUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class UserSession implements Runnable {
	private Chatserver server;
	private Channel channel;
	private Channel aesChannel;
	private String loggedInUser;
	private boolean running = true;
	private byte[] serverChallenge;
	
	public UserSession(Chatserver server, Channel channel) {
		this.channel = channel;
		this.server = server;
		loggedInUser = null;

		handleAuthenticate();
	}

	public void handleAuthenticate() {
		this.channel = new Base64Channel(this.channel);
		byte[] request = null;
		byte[] conv = null;
		String msg = null;
		String username = null;

		try{
			request = (byte[]) this.channel.read();
			//System.out.println("1. empfangene Nachricht: "+request);
			String path = new Config("chatserver").getString("key");
			//System.out.println(path);
			PrivateKey privateKey = Keys.readPrivatePEM(new File(path));
			Cipher cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");

			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			msg = new String(cipher.doFinal(request), Charset.defaultCharset());

			final String B64 = "a -zA -Z0 -9/+ " ;
			assert msg.matches ("!authenticate [\\ w \\.]+ ["+B64+"]{43}=") : " 1 st message ";

			if(msg.matches ("!authenticate [\\w\\.]+ ["+B64+"]{43}=")){
				System.out.println("1. Nachricht OK");
			}else{
				System.out.println("1. Nachricht FALSCH");
			}

			//System.out.println("1. Nachricht entschlüsselt " + msg);

			String[] parts = msg.split("\\s");
			if(parts.length == 3){
				username = parts[1];
				String clientChallenge = parts[2];
				byte[] serverChallenge = SecurityUtils.generateEncryptedSecureRandom();
				this.serverChallenge = serverChallenge;
				byte[] initVector = Base64.encode(SecurityUtils.createInitVector());
				byte[] sessionKey = Base64.encode(SecurityUtils.createSessionKey());

				aesChannel = new AESChannel(this.channel, sessionKey, initVector);

				msg = "!ok " + clientChallenge + " " + new String(serverChallenge, Charset.defaultCharset()) +
						" " + new String(sessionKey, Charset.defaultCharset()) + " " + new String(initVector, Charset.defaultCharset());

				assert msg.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}==") : " 2 nd message ";

				if(msg.matches("!ok ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{43}= ["+B64+"]{22}==")){
					System.out.println("2. Nachricht OK");
				}else{
					System.out.println("2. Nachricht FALSCH ");
				}


				//System.out.println("2. Nachricht bevor gesendet: " + msg);

				File file = new File("keys/chatserver/" + parts[1] + ".pub.pem");

				if(file.exists() == false){
					System.out.println("No public key found for user");
				}

				PublicKey publicKey = Keys.readPublicPEM(file);
				Cipher cipherOk = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
				cipherOk.init(Cipher.ENCRYPT_MODE, publicKey);
				conv = cipherOk.doFinal(msg.getBytes());

				//System.out.println("2. Nachricht verschluesselt "+ conv);

				channel.write(conv);
				this.channel = aesChannel;

				System.out.println("2. Nachricht gesendet");
			}

		} catch (IOException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		} catch (NoSuchPaddingException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		} catch (BadPaddingException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		} catch (IllegalBlockSizeException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		} catch (InvalidKeyException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		} catch (ChannelException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		}

		try {
			byte[] thirdMessage = (byte[]) channel.read();

			String thirdMsg = new String(thirdMessage, Charset.defaultCharset());
			String compare = new String(serverChallenge, Charset.defaultCharset());

			final String B64 = "a -zA -Z0 -9/+ " ;
			assert thirdMsg.matches("["+B64+"]{43}=") : "3rd message ";


			if(thirdMsg.equals(compare)){
				System.out.println("3. Nachricht OK");
				System.out.println("Authentication successful");
				loggedInUser = username;

				AESChannel aesChannelTemp = (AESChannel) aesChannel;
				aesChannelTemp.setActive(true);
				this.channel = aesChannelTemp;
				server.authenticate(loggedInUser, this.channel);
			}else{
				System.out.println("Authentication failed");
				System.out.println("3. Nachricht FALSCH");
			}
		} catch (ChannelException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		} catch (IOException e) {
			System.out.println("Handshake error: " + e.getLocalizedMessage());
		}
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
				System.out.println("Error: " + e.getLocalizedMessage());
			} catch (IOException e) {
				System.out.println("Error: " + e.getLocalizedMessage());
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