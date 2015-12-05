package channel;

public class Base64Channel implements Channel {
	private Channel channel;
	
	public Base64Channel(Channel channel) {
		this.channel = channel;
	}
	
	@Override
	public void write(Object o) throws ChannelException {
		// ... do encoding here
		
		channel.write(o);		
	}

	@Override
	public Object read() throws ChannelException {
		Object o = channel.read();
		// ... do decoding here
		
		return o;
	}

	@Override
	public void close() {
		channel.close();
	}

}
