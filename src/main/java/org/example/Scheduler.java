package org.example;

/**
 * Интерфейс планировщика задач.
 */
public interface Scheduler {
    void execute(Runnable task);
}
