/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sun.nio.ch;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.spi.SelectorProvider;
import java.io.FileDescriptor;
import java.net.InetSocketAddress;

/**
 *
 * @author lprimak
 */
class SocketChannelImplWithBlockingDisabled extends SocketChannelImpl {
    SocketChannelImplWithBlockingDisabled(SelectorProvider sp) throws IOException {
        super(sp);
    }

    SocketChannelImplWithBlockingDisabled(SelectorProvider sp, ProtocolFamily family) throws IOException {
        super(sp, family);
    }

    SocketChannelImplWithBlockingDisabled(SelectorProvider sp,
                      ProtocolFamily family,
                      FileDescriptor fd,
                      InetSocketAddress isa)
        throws IOException {
        super(sp, family, fd, isa);
    }
}
