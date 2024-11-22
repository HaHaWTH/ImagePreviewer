package io.wdsj.imagepreviewer.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Utility class for virtual threads introduced in Java 21.
 */
public class VirtualThreadUtil {
    private static final Method METHOD_THREAD_OF_VIRTUAL;
    private static final Method METHOD_VIRTUAL_THREAD_FACTORY;
    private static final Method METHOD_VIRTUAL_THREAD_PER_TASK_EXECUTOR;

    static {
        Method threadOfVirtual;
        Method virtualThreadFactory;
        Method virtualThreadPerTaskExecutor;

        try {
            threadOfVirtual = Thread.class.getMethod("ofVirtual");
            Class<?> threadBuilder = Class.forName("java.lang.Thread$Builder");
            virtualThreadFactory = threadBuilder.getMethod("factory");
            threadOfVirtual.setAccessible(true);
            virtualThreadFactory.setAccessible(true);
        } catch (Exception e) {
            threadOfVirtual = null;
            virtualThreadFactory = null;
        }

        try {
            virtualThreadPerTaskExecutor = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            virtualThreadPerTaskExecutor.setAccessible(true);
        } catch (Exception e) {
            virtualThreadPerTaskExecutor = null;
        }

        METHOD_THREAD_OF_VIRTUAL = threadOfVirtual;
        METHOD_VIRTUAL_THREAD_FACTORY = virtualThreadFactory;
        METHOD_VIRTUAL_THREAD_PER_TASK_EXECUTOR = virtualThreadPerTaskExecutor;
    }

    /**
     * Creates a new virtual thread factory.
     *
     * @return a virtual thread factory or null if not available.
     */
    public static ThreadFactory newVirtualThreadFactory() {
        return invokeOfVirtualFactory();
    }

    /**
     * Creates a new virtual thread per task executor.
     *
     * @return an executor service backed by virtual threads or null if not available.
     */
    public static ExecutorService newVirtualThreadPerTaskExecutor() {
        return invokeNewVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates a virtual thread factory or falls back to the provided thread factory.
     *
     * @param threadFactory fallback thread factory.
     * @return a virtual thread factory or the provided factory.
     */
    public static ThreadFactory newVirtualThreadFactoryOrProvided(ThreadFactory threadFactory) {
        ThreadFactory factory = invokeOfVirtualFactory();
        return factory != null ? factory : threadFactory;
    }

    /**
     * Creates a virtual thread factory or falls back to the default thread factory.
     *
     * @return a virtual thread factory or the default factory.
     */
    public static @NotNull ThreadFactory newVirtualThreadFactoryOrDefault() {
        ThreadFactory factory = invokeOfVirtualFactory();
        return factory != null ? factory : Executors.defaultThreadFactory();
    }

    /**
     * Creates a virtual thread per task executor or falls back to the provided executor service.
     *
     * @param executorService fallback executor service.
     * @return a virtual thread executor service or the provided executor service.
     */
    @SuppressWarnings("all")
    public static ExecutorService newVirtualThreadPerTaskExecutorOrProvided(ExecutorService executorService) {
        ExecutorService executor = invokeNewVirtualThreadPerTaskExecutor();
        return executor != null ? executor : executorService;
    }

    private static @Nullable ExecutorService invokeNewVirtualThreadPerTaskExecutor() {
        try {
            return (ExecutorService) METHOD_VIRTUAL_THREAD_PER_TASK_EXECUTOR.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable ThreadFactory invokeOfVirtualFactory() {
        try {
            return (ThreadFactory) METHOD_VIRTUAL_THREAD_FACTORY.invoke(METHOD_THREAD_OF_VIRTUAL.invoke(null));
        } catch (Exception e) {
            return null;
        }
    }
}