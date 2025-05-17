package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Базовый класс реактивного потока.
 */
public class Observable<T> {

    @FunctionalInterface
    public interface OnSubscribe<T> {
        void call(Observer<T> observer);
    }

    private final OnSubscribe<T> onSubscribe;

    private Observable(OnSubscribe<T> onSub) {
        this.onSubscribe = onSub;
    }

    public static <U> Observable<U> create(OnSubscribe<U> onSub) {
        return new Observable<>(onSub);
    }

    public Disposable subscribe(
            Consumer<? super T> onNext,
            Consumer<Throwable> onError,
            Runnable onComplete
    ) {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Observer<T> actual = new Observer<>() {
            @Override
            public void onNext(T item) {
                if (!cancelled.get()) onNext.accept(item);
            }
            @Override
            public void onError(Throwable t) {
                if (!cancelled.get()) {
                    cancelled.set(true);
                    onError.accept(t);
                }
            }
            @Override
            public void onComplete() {
                if (!cancelled.get()) {
                    cancelled.set(true);
                    onComplete.run();
                }
            }
        };

        try {
            onSubscribe.call(actual);
        } catch (Throwable ex) {
            actual.onError(ex);
        }

        return new Disposable() {
            @Override
            public void dispose() {
                cancelled.set(true);
            }
            @Override
            public boolean isDisposed() {
                return cancelled.get();
            }
        };
    }

    public Observable<T> subscribeOn(Scheduler scheduler) {
        return create(observer ->
                scheduler.execute(() -> {
                    try {
                        onSubscribe.call(observer);
                    } catch (Throwable ex) {
                        observer.onError(ex);
                    }
                })
        );
    }

    public Observable<T> observeOn(Scheduler scheduler) {
        return create(observer ->
                this.subscribe(
                        item -> scheduler.execute(() -> observer.onNext(item)),
                        err  -> scheduler.execute(() -> observer.onError(err)),
                        ()   -> scheduler.execute(observer::onComplete)
                )
        );
    }

    public <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return create(subObs ->
                this.subscribe(
                        t -> subObs.onNext(mapper.apply(t)),
                        subObs::onError,
                        subObs::onComplete
                )
        );
    }

    public Observable<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return create(subObs ->
                this.subscribe(
                        t -> {
                            if (predicate.test(t)) subObs.onNext(t);
                        },
                        subObs::onError,
                        subObs::onComplete
                )
        );
    }


    public <R> Observable<R> flatMap(Function<? super T, Observable<R>> mapper) {
        Objects.requireNonNull(mapper);
        return create(subObs ->
                this.subscribe(
                        t -> {
                            Observable<R> inner = mapper.apply(t);
                            List<R> buffer = new ArrayList<>();
                            AtomicBoolean errorOccurred = new AtomicBoolean(false);

                            inner.subscribe(
                                    r -> {
                                        if (!errorOccurred.get()) {
                                            buffer.add(r);
                                        }
                                    },
                                    err -> {
                                        // при первой ошибке чистим буфер и прокидываем ошибку
                                        errorOccurred.set(true);
                                        subObs.onError(err);
                                    },
                                    () -> {
                                        if (!errorOccurred.get()) {
                                            // flush buffer
                                            for (R r : buffer) {
                                                subObs.onNext(r);
                                            }
                                        }
                                    }
                            );
                        },
                        subObs::onError,
                        subObs::onComplete
                )
        );
    }
}
