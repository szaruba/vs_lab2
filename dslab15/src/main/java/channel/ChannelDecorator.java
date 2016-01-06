package channel;

import java.io.IOException;

/**
 * Created by Markus on 05.01.2016.
 */
public class ChannelDecorator implements Channel{
    protected Channel channel;

    public ChannelDecorator(Channel c){
        this.channel = c;
    }

    @Override
    public void write(Object o) throws ChannelException, IOException {
        channel.write(o);
    }

    @Override
    public Object read() throws ChannelException, IOException {
        return channel.read();
    }

    @Override
    public void close() {
        channel.close();
    }
}
