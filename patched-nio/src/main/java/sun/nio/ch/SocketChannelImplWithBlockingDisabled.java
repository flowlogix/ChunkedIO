/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sun.nio.ch;

import com.flowlogix.nio.ch.GetSetOptions;
import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.spi.SelectorProvider;
import java.io.FileDescriptor;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import sun.net.*;

/**
 *
 * @author lprimak
 */
class SocketChannelImplWithBlockingDisabled extends SocketChannelImpl {
    boolean useHighPerformance;
    private static final VarHandle connectionResetHandle;
    private static final NativeDispatcher nd = new SocketDispatcher();
    private static final VarHandle fdHandle;

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

    static {
        try {
            connectionResetHandle = MethodHandles.privateLookupIn(SocketChannelImpl.class, MethodHandles.lookup())
                    .findVarHandle(SocketChannelImpl.class, "connectionReset", boolean.class);
            fdHandle = MethodHandles.privateLookupIn(SocketChannelImpl.class, MethodHandles.lookup())
                    .findVarHandle(SocketChannelImpl.class, "fd", FileDescriptor.class);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        Objects.requireNonNull(buf);
        int n = 0;
        try {
            // check if connection has been reset
            if ((boolean)connectionResetHandle.get(this)) {
                throwConnectionReset();
            }
            n = IOUtil.read((FileDescriptor)fdHandle.get(this), buf, -1, nd);
        } catch (ConnectionResetException e) {
            connectionResetHandle.set(this, true);
            throwConnectionReset();
        } finally {
            if (n <= 0) {
                return IOStatus.EOF;
            }
        }
        return IOStatus.normalize(n);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException
    {
        Objects.checkFromIndexSize(offset, length, dsts.length);
        long n = 0;
        try {
            // check if connection has been reset
            if ((boolean)connectionResetHandle.get(this)) {
                throwConnectionReset();
            }
            n = IOUtil.read((FileDescriptor)fdHandle.get(this), dsts, offset, length, nd);
        } catch (ConnectionResetException e) {
            connectionResetHandle.set(this, true);
            throwConnectionReset();
        } finally {
            if (n <= 0) {
                return IOStatus.EOF;
            }
        }
        return IOStatus.normalize(n);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> name)
        throws IOException
    {
        return GetSetOptions.getOption(name, () -> useHighPerformance, super::getOption);
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value)
        throws IOException {
        GetSetOptions.setOption(name, value, (tf) -> useHighPerformance = tf, super::setOption);
        return this;
    }

    private void throwConnectionReset() throws SocketException {
        throw new SocketException("Connection reset");
    }
}
