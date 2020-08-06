/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.nio.ch;

import java.io.IOException;
import java.net.SocketOption;

/**
 *
 * @author lprimak
 */
public class GetSetOptions {
    private static final String highPerformanceOptionName = "UseHighPerformanceSockets";

    @FunctionalInterface
    public interface OptionGetter {
        boolean get();
    }

    @FunctionalInterface
    public interface OptionSetter {
        void set(boolean tf);
    }

    @FunctionalInterface
    public interface SuperGetOptionFn<T> {
        T getOption(SocketOption<T> name) throws IOException;
    }

    @FunctionalInterface
    public interface SuperSetOptionFn<T> {
        void setOption(SocketOption<T> name, T value) throws IOException;
    }

    @SuppressWarnings("unchecked")
    static public <T> T getOption(SocketOption<T> name, OptionGetter getter, SuperGetOptionFn<T> fn)
        throws IOException
    {
        if (name.name().equals(highPerformanceOptionName)) {
            return (T)(Boolean)getter.get();
        } else {
            return fn.getOption(name);
        }
    }

    static public <T> void setOption(SocketOption<T> name, T value, OptionSetter setter, SuperSetOptionFn<T> fn)
        throws IOException {
        if (name.name().equals(highPerformanceOptionName)) {
            setter.set((Boolean)value);
        } else {
            fn.setOption(name, value);
        }
    }

}
