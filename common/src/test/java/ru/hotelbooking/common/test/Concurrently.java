package ru.hotelbooking.common.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * Запускает задачи в отдельных потоках в один и тот же момент, чтобы спровоцировать гонки; возвращает результаты в
 * порядке отправки.
 */
public final class Concurrently {

    private Concurrently() {}

    public static <T> List<T> run(int threads, Callable<T> task) throws Exception {
        return run(threads, index -> task);
    }

    /** {@code taskFactory} получает номер потока, начиная с 0. */
    public static <T> List<T> run(int threads, IntFunction<Callable<T>> taskFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<T> task = taskFactory.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
