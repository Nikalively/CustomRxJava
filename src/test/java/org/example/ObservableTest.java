package org.example;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ObservableTest {

    @Test
    void testSimpleOnNextAndOnComplete() {
        AtomicReference<Integer> last = new AtomicReference<>(0);
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable.<Integer>create(obs -> {
            obs.onNext(5);
            obs.onNext(10);
            obs.onComplete();
        }).subscribe(
                last::set,
                err -> fail("Ошибок не ожидается"),
                () -> completed.set(true)
        );

        assertTrue(completed.get(), "Должен произойти onComplete");
        assertEquals(10, last.get().intValue());
    }

    @Test
    void testOnErrorPropagation() {
        String msg = "TestError";
        AtomicReference<Throwable> errRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable.<String>create(obs -> {
            obs.onNext("A");
            throw new RuntimeException(msg);
        }).subscribe(
                s -> fail("Не ожидается onNext"),
                err -> errRef.set(err),
                () -> completed.set(true)
        );

        assertFalse(completed.get());
        assertNotNull(errRef.get());
        assertEquals(msg, errRef.get().getMessage());
    }
}