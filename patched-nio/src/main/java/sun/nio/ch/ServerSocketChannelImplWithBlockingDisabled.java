/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sun.nio.ch;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.io.FileDescriptor;
import java.net.ProtocolFamily;


/**
 *
 * @author lprimak
 */
public class ServerSocketChannelImplWithBlockingDisabled extends ServerSocketChannelImpl {
    ServerSocketChannelImplWithBlockingDisabled(SelectorProvider sp) {
        super(sp);
    }

    ServerSocketChannelImplWithBlockingDisabled(SelectorProvider sp, ProtocolFamily family) {
        super(sp, family);
    }

    ServerSocketChannelImplWithBlockingDisabled(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException {
            super(sp, fd, bound);
    }



    @Override
    public SocketChannel accept() throws IOException {
        System.out.println("Calling Accept");
        return super.accept();
    }
}
