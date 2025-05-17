package org.example;

import schedulers.ComputationScheduler;
import schedulers.IOThreadScheduler;
import schedulers.SingleThreadScheduler;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerIntegrationTest {

    @Test
    void testObserveOnWithDifferentSchedulers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> obsThread = new AtomicReference<>();
        String main = Thread.currentThread().getName();

        Observable.<Integer>create(o -> {
                    o.onNext(1);
                    o.onComplete();
                })
                .observeOn(new ComputationScheduler())
                .subscribe(
                        i -> obsThread.set(Thread.currentThread().getName()),
                        err -> fail("Unexpected"),
                        latch::countDown
                );

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotEquals(main, obsThread.get());
    }

    @Test
    void testSingleThreadSchedulerSequentialExecution() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        List<String> names = new ArrayList<>();
        SingleThreadScheduler sched = new SingleThreadScheduler();

        for (int i = 0; i < 2; i++) {
            int finalI = i;
            Observable.<Integer>create(o -> {
                        names.add(Thread.currentThread().getName());
                        o.onNext(finalI);
                        o.onComplete();
                    })
                    .subscribeOn(sched)
                    .subscribe(
                            x -> {},
                            err -> fail(),
                            latch::countDown
                    );
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(names.get(0), names.get(1));
    }

    @Test
    void testDisposableWithMultipleSubscriptions() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> last = new AtomicReference<>(0);

        Observable<Integer> src = Observable.create(o -> {
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                o.onNext(i++);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Disposable d1 = src
                .subscribeOn(new IOThreadScheduler())
                .subscribe(
                        last::set,
                        err -> fail(),
                        () -> {}
                );
        Disposable d2 = src
                .subscribeOn(new IOThreadScheduler())
                .subscribe(
                        i -> {},
                        err -> fail(),
                        () -> {}
                );

        Thread.sleep(200);
        d1.dispose();
        d2.dispose();
        int v = last.get();
        Thread.sleep(100);
        assertEquals(v, last.get());
    }

    @Test
    void testErrorHandlingInConcurrentOperations() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errRef = new AtomicReference<>();
        String msg = "Err";

        Observable.<Integer>create(o -> {
                    o.onNext(1);
                    throw new RuntimeException(msg);
                })
                .subscribeOn(new IOThreadScheduler())
                .observeOn(new ComputationScheduler())
                .subscribe(
                        i -> fail(),
                        e -> { errRef.set(e); latch.countDown(); },
                        () -> fail()
                );

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(msg, errRef.get().getMessage());
    }
}
