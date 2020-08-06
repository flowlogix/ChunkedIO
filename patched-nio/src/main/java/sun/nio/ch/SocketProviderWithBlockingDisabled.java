/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sun.nio.ch;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 *
 * @author lprimak
 */
public class SocketProviderWithBlockingDisabled extends KQueueSelectorProvider {
    @Override
    public SocketChannel openSocketChannel() throws IOException {
        return new sun.nio.ch.SocketChannelImplWithBlockingDisabled(this);
    }

    @Override
    public SocketChannel openSocketChannel(ProtocolFamily family) throws IOException {
        return new sun.nio.ch.SocketChannelImplWithBlockingDisabled(this, family);
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() throws IOException {
        return new sun.nio.ch.ServerSocketChannelImplWithBlockingDisabled(this);
    }

    @Override
    public ServerSocketChannel openServerSocketChannel(ProtocolFamily family) {
        return new sun.nio.ch.ServerSocketChannelImplWithBlockingDisabled(this, family);
    }
}
