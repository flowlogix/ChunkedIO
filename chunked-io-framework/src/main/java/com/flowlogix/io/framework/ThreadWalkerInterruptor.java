/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 *
 * @author lprimak
 */
public class ThreadWalkerInterruptor {
    private final Transport transport;
    private final ConcurrentLinkedQueue<TaskTime> threadList;
    private final Thread interruptorThread = new Thread(Transport.logExceptions(this::walkAndInterrupt), "Thread-Walker-Interruptor");
    private final Thread loadChecker = new Thread(Transport.logExceptions(this::checkLoad), "Load-Checker");
    private volatile boolean started;
    private final long timeout;
    private final long idleTimeout;
    private final long underLoadTimeoutNanos;
    private final AtomicBoolean isUnderLoad = new AtomicBoolean();
    private int underLoadResetCount = 0;


    void start() {
        started = true;
        interruptorThread.start();
        loadChecker.start();
    }

    void stop() {
        try {
            started = false;
            LockSupport.unpark(interruptorThread);
            interruptorThread.join();
            LockSupport.unpark(loadChecker);
            loadChecker.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    static class TaskTime {
        final String operation;
        long thread;
        long timeStarted;

        public TaskTime(String operation, long thread, long timeStarted) {
            this.operation = operation;
            this.thread = thread;
            this.timeStarted = timeStarted;
        }

        void taskFinished() {
            thread = -1;
        }

        @Override
        public String toString() {
            return String.format("Operation: %s, Thread: %d, Time: %d", operation, thread, timeStarted);
        }
    };

    ThreadWalkerInterruptor(Transport transport) {
        this.transport = transport;
        threadList = new ConcurrentLinkedQueue<>();
        timeout = transport.props.getProperty(IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS);
        idleTimeout = transport.props.getProperty(IOProperties.Props.EVENTS_IDLE_TIMEOUT_IN_MILLIS);
        underLoadTimeoutNanos = transport.props.getProperty(IOProperties.Props.EVENTS_UNDER_LOAD_TIMEOUT_NANOS);
    }

    TaskTime addTask(String operation, long thr) {
        TaskTime taskTime = new TaskTime(operation, thr, System.currentTimeMillis());
        threadList.offer(taskTime);
        return taskTime;
    }

    public void setUnderLoad() {
        if (!isUnderLoad.getAndSet(true)) {
            LockSupport.unpark(interruptorThread);
        }
    }

    private void checkLoad() {
        while (started) {
            if (isUnderLoad.get()) {
                if (transport.readExec.getQueue().isEmpty() && transport.writeExec.getQueue().isEmpty()) {
                    ++underLoadResetCount;
                    if (underLoadResetCount == 10) {
                        underLoadResetCount = 0;
                        isUnderLoad.set(false);
                    }
                }
            }
            LockSupport.parkNanos(idleTimeout * 100 * 1000000);
        }
    }

    private void walkAndInterrupt() {
        try {
            while (started) {
                var iterator = threadList.iterator();
                long pastTimeOut = System.currentTimeMillis() - timeout;
                while (iterator.hasNext()) {
                    TaskTime taskTime = iterator.next();
                    if (taskTime.thread == -1) {
                        // task finished
                        iterator.remove();
                    } else if (taskTime.timeStarted < pastTimeOut) {
                        transport.interrupt(taskTime.thread);
                    }
                }
                LockSupport.parkNanos(isUnderLoad.get() ? underLoadTimeoutNanos : idleTimeout * 1000000);
            }
        } catch (IOException ex) {
            if (started) {
                throw new RuntimeException(ex);
            }
        }
    }
}
