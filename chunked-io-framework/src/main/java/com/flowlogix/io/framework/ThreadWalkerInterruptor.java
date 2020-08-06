/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import java.util.LinkedList;

/**
 *
 * @author lprimak
 */
public class ThreadWalkerInterruptor {
    private final Transport transport;
    private final LinkedList<TaskTime> threadList;
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
        Thread thread;
        long timeStarted;

        public TaskTime(Thread thread, long timeStarted) {
            this.thread = thread;
            this.timeStarted = timeStarted;
        }

        void taskFinished() {
            thread = null;
        }
    };

    ThreadWalkerInterruptor(Transport transport) {
        this.transport = transport;
        threadList = new LinkedList<>();
    }

    TaskTime addTask(Thread thr) {
        TaskTime taskTime = new TaskTime(thr, System.currentTimeMillis());
        threadList.add(taskTime);
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
                    if (taskTime.thread == null) {
                        // task finished
                        iterator.remove();
                    } else if (taskTime.timeStarted < pastTimeOut) {
                        taskTime.thread.interrupt();
                    }
                }
                Thread.sleep(timeout);
            } catch (InterruptedException ex) {
                if (started) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}
