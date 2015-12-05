package channel;

public interface Channel {
	void write(Object o) throws ChannelException;
	Object read() throws ChannelException;
	void close();
}
