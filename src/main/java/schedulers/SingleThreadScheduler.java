package schedulers;

import org.example.Scheduler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Однопоточный планировщик.
 */
public class SingleThreadScheduler implements Scheduler {
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SingleThread");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void execute(Runnable task) {
        executor.submit(task);
    }
}
