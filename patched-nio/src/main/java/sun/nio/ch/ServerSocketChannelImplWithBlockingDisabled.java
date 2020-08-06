/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sun.nio.ch;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.spi.SelectorProvider;
import java.io.FileDescriptor;
import java.net.ProtocolFamily;
import java.net.SocketOption;
import java.net.InetSocketAddress;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 *
 * @author lprimak
 */
public class ServerSocketChannelImplWithBlockingDisabled extends ServerSocketChannelImpl {
    private static final NativeDispatcher nd = new SocketDispatcher();
    private static final String highPerformanceOptionName = "UseHighPerformanceSockets";
    private static final VarHandle fdHandle;
    private static final VarHandle familyHandle;
    private static final VarHandle stateHandle;
    private static final int ST_CLOSED;
    private boolean useHighPerformance = false;


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

    static {
        try {
            fdHandle = MethodHandles.privateLookupIn(ServerSocketChannelImpl.class, MethodHandles.lookup())
                    .findVarHandle(ServerSocketChannelImpl.class, "fd", FileDescriptor.class);
            familyHandle = MethodHandles.privateLookupIn(ServerSocketChannelImpl.class, MethodHandles.lookup())
                   .findVarHandle(ServerSocketChannelImpl.class, "family", ProtocolFamily.class);
            stateHandle = MethodHandles.privateLookupIn(ServerSocketChannelImpl.class, MethodHandles.lookup())
                   .findVarHandle(ServerSocketChannelImpl.class, "state", int.class);
            ST_CLOSED = (int)MethodHandles.privateLookupIn(ServerSocketChannelImpl.class, MethodHandles.lookup())
                   .findStaticVarHandle(ServerSocketChannelImpl.class, "ST_CLOSED", int.class).get();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> name)
        throws IOException
    {
        if (name.name().equals(this.highPerformanceOptionName)) {
            return (T)Boolean.TRUE;
        } else {
            return super.getOption(name);
        }
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value)
        throws IOException {
        if (name.name().equals(this.highPerformanceOptionName)) {
            this.useHighPerformance = (Boolean)value;
            return this;
        } else {
            return super.setOption(name, value);
        }
    }

    @Override
    public SocketChannel accept() throws IOException {
        if (useHighPerformance) {
            return doAccept();
        } else {
            return super.accept();
        }
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        if (useHighPerformance) {
            stateHandle.set(this, ST_CLOSED);
            nd.close((FileDescriptor)fdHandle.get(this));
        }
        else {
            super.implCloseSelectableChannel();
        }
    }

    private SocketChannel doAccept() throws IOException {
        int n = 0;
        FileDescriptor newfd = new FileDescriptor();
        InetSocketAddress[] isaa = new InetSocketAddress[1];

        try {
            n = Net.accept((FileDescriptor)fdHandle.get(this), newfd, isaa);
        } catch (IOException ex) {
            if (!isOpen()) {
                throw new ClosedChannelException();
            } else {
                throw ex;
            }
        }
        if (n > 0) {
            return finishAccept(newfd, isaa[0]);
        } else {
            return null;
        }
    }

    private SocketChannel finishAccept(FileDescriptor newfd, InetSocketAddress isa)
        throws IOException
    {
        try {
            // check permitted to accept connections from the remote address
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkAccept(isa.getAddress().getHostAddress(), isa.getPort());
            }
            return new SocketChannelImplWithBlockingDisabled(provider(), (ProtocolFamily)familyHandle.get(this), newfd, isa);
        } catch (Exception e) {
            nd.close(newfd);
            throw e;
        }
    }
}
