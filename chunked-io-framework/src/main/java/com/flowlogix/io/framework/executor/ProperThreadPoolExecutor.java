/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework.executor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ScalingThreadPool doesn't scale down
 *
 * @author lprimak
 */
public class ProperThreadPoolExecutor extends ThreadPoolExecutor {
    private final BlockingQueue<Runnable> workQueue;

    private static final VarHandle ctlHandle;
    private static final VarHandle corePoolSizeHandle;
    private static final int COUNT_MASK;
    private static final MethodHandle addWorkerHandle;
    private static final MethodHandle isRunningHandle;
    private static final MethodHandle rejectHandle;

    static {
        try {
            ctlHandle = MethodHandles.privateLookupIn(ThreadPoolExecutor.class, MethodHandles.lookup())
                    .findVarHandle(ThreadPoolExecutor.class, "ctl", AtomicInteger.class);
            corePoolSizeHandle = MethodHandles.privateLookupIn(ThreadPoolExecutor.class, MethodHandles.lookup())
                    .findVarHandle(ThreadPoolExecutor.class, "corePoolSize", int.class);
            COUNT_MASK = (int)MethodHandles.privateLookupIn(ThreadPoolExecutor.class, MethodHandles.lookup())
                    .findStaticVarHandle(ThreadPoolExecutor.class, "COUNT_MASK", int.class).get();

            addWorkerHandle = MethodHandles.privateLookupIn(ThreadPoolExecutor.class, MethodHandles.lookup())
                    .findVirtual(ThreadPoolExecutor.class, "addWorker", MethodType.methodType(boolean.class, List.of(Runnable.class, boolean.class)));
            isRunningHandle = MethodHandles.privateLookupIn(ThreadPoolExecutor.class, MethodHandles.lookup())
                    .findStatic(ThreadPoolExecutor.class, "isRunning", MethodType.methodType(boolean.class, int.class));
            rejectHandle = MethodHandles.privateLookupIn(ThreadPoolExecutor.class, MethodHandles.lookup())
                    .findVirtual(ThreadPoolExecutor.class, "reject", MethodType.methodType(void.class, Runnable.class));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ProperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), threadFactory);
    }

    ProperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.workQueue = workQueue;
    }

    @Override
    public void execute(Runnable command) {
        int c = getCtl();
        if (workerCountOf(c) < (int)corePoolSizeHandle.get(this)) {
            if (addWorker(command, true)) {
                return;
            }
            c = getCtl();
        }
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = getCtl();
            if (!isRunning(recheck) && remove(command)) {
                reject(command);
            } else if (workerCountOf(recheck) == 0) {
                addWorker(null, false);
            }
        } else if (!addWorker(command, false)) {
            reject(command);
        }
    }

    private int getCtl() {
        AtomicInteger ctl = (AtomicInteger)ctlHandle.get(this);
        return ctl.get();
    }

    private int workerCountOf(int c) {
        return c & COUNT_MASK;
    }

    private boolean addWorker(Runnable command, boolean b) {
        try {
            return (boolean)addWorkerHandle.invoke(this, command, b);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean isRunning(int c) {
        try {
            return (boolean)isRunningHandle.invoke(c);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    private void reject(Runnable command) {
        try {
            rejectHandle.invoke(this, command);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
