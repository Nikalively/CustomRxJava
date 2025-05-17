package org.example;

import schedulers.IOThreadScheduler;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedScenariosTest {

    @Test
    void testDisposableStopsEmission() throws InterruptedException {
        Observable<Integer> src = Observable.create(obs -> {
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                obs.onNext(i++);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        AtomicInteger last = new AtomicInteger();
        Disposable d = src
                .subscribeOn(new IOThreadScheduler())
                .subscribe(last::set, e -> {}, () -> {});

        Thread.sleep(50);
        d.dispose();
        int snapshot = last.get();
        Thread.sleep(50);
        assertEquals(snapshot, last.get(), "После dispose больше не должно быть onNext");
    }

    @Test
    void testErrorInInnerFlatMapDoesNotCrashOuter() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errRef = new AtomicReference<>();

        Observable.<Integer>create(obs -> {
                    obs.onNext(1);
                    obs.onComplete();
                })
                .flatMap(i ->
                        Observable.<String>create(inner -> {
                            inner.onNext("ok");
                            inner.onError(new RuntimeException("inner fail"));
                        })
                )
                .subscribe(
                        s -> fail("onNext не ожидается"),
                        err -> { errRef.set(err); latch.countDown(); },
                        () -> {}
                );

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("inner fail", errRef.get().getMessage());
    }
}