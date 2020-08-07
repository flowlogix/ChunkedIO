/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author lprimak
 */
public class ThreadWalkerInterruptor {
    private final Transport transport;
    private final ConcurrentLinkedQueue<TaskTime> threadList;
    private Thread interruptorThread = new Thread(Transport.logExceptions(this::walkAndInterrupt), "ThreadWalkerInterruptor");
    private volatile boolean started;

    void start() {
        started = true;
        interruptorThread.start();
    }

    void stop() {
        started = false;
        interruptorThread.interrupt();
        if (interruptorThread != null) {
            interruptorThread = null;
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
    }

    TaskTime addTask(String operation, long thr) {
        TaskTime taskTime = new TaskTime(operation, thr, System.currentTimeMillis());
        threadList.offer(taskTime);
        return taskTime;
    }

    private void walkAndInterrupt() {
        while (started) {
            try {
                long timeout = transport.props.getProperty(IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS);
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
                Thread.sleep(timeout);
            } catch (InterruptedException | IOException ex) {
                if (started) {
                    throw new RuntimeException(ex);
                }
            } catch (ConcurrentModificationException ex) {
                // ignore
            }
        }
    }
}
