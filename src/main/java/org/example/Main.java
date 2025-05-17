package org.example;

import schedulers.ComputationScheduler;
import schedulers.IOThreadScheduler;
import schedulers.SingleThreadScheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Начало: базовая эмиссия");
        Observable.<Integer>create(obs -> {
                    obs.onNext(1);
                    obs.onNext(2);
                    obs.onNext(3);
                    obs.onComplete();
                })
                .subscribe(
                        item -> System.out.println("  получено: " + item),
                        err  -> System.err.println("  ошибка: " + err),
                        ()   -> System.out.println("  завершено")
                );

        System.out.println("\nПреобразование: map и filter");
        Observable.<Integer>create(obs -> {
                    for (int i = 1; i <= 5; i++) obs.onNext(i);
                    obs.onComplete();
                })
                .map(x -> x * 10)
                .filter(x -> x % 20 == 0)
                .subscribe(
                        item -> System.out.println("  результат: " + item),
                        err  -> System.err.println("  ошибка: " + err),
                        ()   -> System.out.println("  выполнено")
                );

        System.out.println("\nПреобразование: flatMap");
        Observable.<String>create(obs -> {
                    obs.onNext("A");
                    obs.onNext("B");
                    obs.onComplete();
                })
                .flatMap(ch -> Observable.<String>create(inner -> {
                    inner.onNext(ch + "1");
                    inner.onNext(ch + "2");
                    inner.onComplete();
                }))
                .subscribe(
                        s   -> System.out.println("  элемент: " + s),
                        err -> System.err.println("  ошибка: " + err),
                        ()  -> System.out.println("  завершено")
                );

        System.out.println("\nПотоки: subscribeOn и observeOn");
        CountDownLatch latch = new CountDownLatch(2);
        Observable.<Integer>create(obs -> {
                    obs.onNext(100);
                    obs.onNext(200);
                    obs.onComplete();
                })
                .subscribeOn(new IOThreadScheduler())
                .observeOn(new ComputationScheduler())
                .subscribe(
                        x -> {
                            System.out.printf("  Поток %s получил %d%n",
                                    Thread.currentThread().getName(), x);
                            latch.countDown();
                        },
                        err -> System.err.println("  ошибка: " + err),
                        ()  -> { /* ignore */ }
                );
        // ждём два onNext
        latch.await(2, TimeUnit.SECONDS);

        System.out.println("\nОтмена подписки");
        Observable<Long> ticker = Observable.create(obs -> {
            long i = 0L;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    obs.onNext(i++);
                    Thread.sleep(300);
                }
            } catch (InterruptedException ex) {
                obs.onComplete();
            }
        });
        Disposable disp = ticker
                .subscribeOn(new SingleThreadScheduler())
                .subscribe(
                        i   -> System.out.println("  счетчик: " + i),
                        err -> System.err.println("  ошибка: " + err),
                        ()  -> System.out.println("  счетчик завершен")
                );
        // даем время в 1.5 секунды, потом отменяем
        Thread.sleep(1500);
        disp.dispose();
        System.out.println("  подписка отменена");

        // логи ложаться спать)
        Thread.sleep(500);
        System.out.println("\nПрограмма завершена");
    }
}