package channel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class TcpChannel implements Channel {
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private Socket socket;
	
	public TcpChannel(Socket socket) throws ChannelException {
		this.socket = socket;
		try {
			out = new ObjectOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			throw new ChannelException("Error when creating output stream");
		}
		try {
			in = new ObjectInputStream(socket.getInputStream());
		} catch (IOException e) {
			throw new ChannelException("Error when creating input stream");
		}
	}
	
	public void write(Object o) throws ChannelException {
		try {
			out.writeObject(o);
		} catch (IOException e) {
			throw new ChannelException("Error when writing Object");
		}
	}
	
	public Object read() throws ChannelException {
		try {
			return in.readObject();
		} catch (ClassNotFoundException e) {
			throw new ChannelException("Class not found");
		} catch (IOException e) {
			throw new ChannelException("Error when reading Object");
		}
	}

	@Override
	public void close() {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
