package org.example;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OperatorChainTest {

    @Test
    void testMapFilterChain() {
        List<Integer> results = new ArrayList<>();

        Observable.<Integer>create(obs -> {
                    obs.onNext(1);
                    obs.onNext(2);
                    obs.onNext(3);
                    obs.onComplete();
                })
                .filter(x -> x % 2 == 1)   // 1,3
                .map(x -> x * 10)          // 10,30
                .subscribe(
                        results::add,
                        err -> fail("Ошибка не ожидается"),
                        () -> {}
                );

        assertEquals(List.of(10, 30), results);
    }

    @Test
    void testFlatMap() {
        List<String> out = new ArrayList<>();

        Observable.<Integer>create(obs -> {
                    obs.onNext(1);
                    obs.onNext(2);
                    obs.onComplete();
                })
                .flatMap(i ->
                        Observable.<String>create(inner -> {
                            inner.onNext("X" + i);
                            inner.onComplete();
                        })
                )
                .subscribe(
                        out::add,
                        err -> fail(),
                        () -> {}
                );

        assertEquals(List.of("X1", "X2"), out);
    }
}