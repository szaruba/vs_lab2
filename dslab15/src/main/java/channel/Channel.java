package channel;

import java.io.IOException;

public interface Channel {
	void write(Object o) throws ChannelException, IOException;
	Object read() throws ChannelException, IOException;
	void close();
}
