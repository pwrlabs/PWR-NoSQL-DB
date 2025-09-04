package main;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Settings {
    public static final ThreadPoolExecutor nonDaemonExecutor = new ThreadPoolExecutor(
            1,
            1000000,
            1L,
            TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            r -> {
                Thread t = new Thread(r);
                t.setName("NonDaemonThread-" + r.hashCode() + "-" + System.currentTimeMillis());
                t.setDaemon(false);  // Make non-daemon threads
                return t;
            }
    );
}
