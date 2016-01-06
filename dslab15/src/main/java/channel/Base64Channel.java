package channel;

import org.bouncycastle.util.encoders.Base64;

import java.io.*;

public class Base64Channel extends ChannelDecorator {
	public Base64Channel(Channel c) {
		super(c);
	}

	@Override
	public void write(Object o) throws IOException, ChannelException {
		byte[] b = (byte[]) o;
		byte[] enB = Base64.encode(b);
		super.write(enB);
	}

	@Override
	public Object read() throws IOException, ChannelException {
		byte[] enB = (byte[]) super.read();
		byte[] b = Base64.decode(enB);
		return b;
	}


}

