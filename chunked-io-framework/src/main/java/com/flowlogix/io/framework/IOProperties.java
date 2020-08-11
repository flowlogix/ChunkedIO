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
        MAX_IO_THREADS,
        MAX_EXEC_THREADS,
        IO_THREAD_STACK_SIZE,
        USING_SELECT_LOOP,
    }

    private final Object properties[] = new Object[Props.values().length];
    private static final Logger log = Logger.getLogger(IOProperties.class.getName());

    @SuppressWarnings("unchecked")
    public<T> T getProperty(Props propType) {
        Object rv = properties[propType.ordinal()];
        if (rv == null) {
            throw new IllegalStateException(String.format("Can't get property %s - not set", propType.name()));
        }
        return (T)rv;
    }

    public<T> IOProperties setProperty(Props propType, T object) {
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
