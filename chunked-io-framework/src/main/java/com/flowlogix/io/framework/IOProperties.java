/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import java.util.logging.Logger;

/**
 *
 * @author lprimak
 */
public class IOProperties {
    public enum Props {
        ACCEPT_BACKLOG,
        SOCKET_TIMEOUT_IN_MILLIS,
        EVENTS_IDLE_TIMEOUT_IN_MILLIS,
        EVENTS_UNDER_LOAD_TIMEOUT_NANOS,
        MAX_ACCEPT_THREADS,
        MAX_READ_THREADS,
        MAX_WRITE_THREADS,
        IO_THREAD_STACK_SIZE,
        USING_SELECT_LOOP,
    }

    private final Object properties[] = new Object[Props.values().length];
    private static final Logger log = Logger.getLogger(IOProperties.class.getName());

    public IOProperties() {
        // set defaults
        setProperty(IOProperties.Props.ACCEPT_BACKLOG, 4096);
        setProperty(IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS, 100L);
        setProperty(IOProperties.Props.IO_THREAD_STACK_SIZE, 1024);
        setProperty(IOProperties.Props.MAX_ACCEPT_THREADS, 5);
        setProperty(IOProperties.Props.MAX_READ_THREADS, 200);
        setProperty(IOProperties.Props.MAX_WRITE_THREADS, 200);
        setProperty(IOProperties.Props.EVENTS_UNDER_LOAD_TIMEOUT_NANOS, 5L * 1000000);
        setProperty(IOProperties.Props.EVENTS_IDLE_TIMEOUT_IN_MILLIS, 50L);
        setProperty(IOProperties.Props.USING_SELECT_LOOP, false);
    }

    @SuppressWarnings("unchecked")
    public<T> T getProperty(Props propType) {
        Object rv = properties[propType.ordinal()];
        if (rv == null) {
            throw new IllegalStateException(String.format("Can't get property %s - not set", propType.name()));
        }
        return (T)rv;
    }

    final public<T> IOProperties setProperty(Props propType, T object) {
        properties[propType.ordinal()] = object;
        return this;
    }

    boolean exists(Props propType) {
        return properties[propType.ordinal()] != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("IOProperties - [");
        Props[] values = Props.values();
        for (Props prop : values) {
            sb.append(prop.name()).append("=");
            sb.append(properties[prop.ordinal()]);
            if(prop.ordinal() < values.length - 1) {
                sb.append(", ");
            }
        }
        return sb.append("]").toString();
    }
}
