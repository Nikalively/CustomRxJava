package org.example;

/**
 * Интерфейс получателя событий от Observable.
 */
public interface Observer<T> {
    void onNext(T item);
    void onError(Throwable t);
    void onComplete();
}