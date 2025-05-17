package org.example;

import schedulers.IOThreadScheduler;
import schedulers.ComputationScheduler;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    @Test
    void testSubscribeOnDoesNotBlockMainThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        Observable.<Integer>create(obs -> {
                    threadName.set(Thread.currentThread().getName());
                    obs.onNext(1);
                    obs.onComplete();
                })
                .subscribeOn(new IOThreadScheduler())
                .subscribe(
                        i -> {},
                        err -> fail(),
                        latch::countDown
                );

        boolean finished = latch.await(1, TimeUnit.SECONDS);
        assertTrue(finished);
        assertTrue(threadName.get().startsWith("IOThread-"),
                "Должен быть поток IOThreadScheduler");
    }

    @Test
    void testObserveOnSwitchesThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String main = Thread.currentThread().getName();
        AtomicReference<String> calledOn = new AtomicReference<>();

        Observable.<Integer>create(obs -> {
                    obs.onNext(1);
                    obs.onComplete();
                })
                .observeOn(new ComputationScheduler())
                .subscribe(
                        i -> calledOn.set(Thread.currentThread().getName()),
                        err -> fail(),
                        latch::countDown
                );

        latch.await(1, TimeUnit.SECONDS);
        assertNotEquals(main, calledOn.get());
        assertTrue(calledOn.get().startsWith("ComputeThread-"));
    }
}