package org.example;

/**
 * Интерфейс для отмены подписки.
 */
public interface Disposable {
    void dispose();
    boolean isDisposed();
}