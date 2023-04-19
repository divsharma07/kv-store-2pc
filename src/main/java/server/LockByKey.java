package server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A utility class that allows locking by key, and helps implement Two phase commit.
 * This is inspired by the article: https://www.baeldung.com/java-acquire-lock-by-key
 */
public class LockByKey {
  private static final int ALLOWED_THREADS = 1;

  private static ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<String, Semaphore>();

  public void lock(String key) {
    Semaphore semaphore = semaphores.compute(key, (k, v) -> v == null ? new Semaphore(ALLOWED_THREADS) : v);
    semaphore.acquireUninterruptibly();
  }

  public void unlock(String key) {
    Semaphore semaphore = semaphores.get(key);
    semaphore.release();
    if (semaphore.availablePermits() == ALLOWED_THREADS) {
      semaphores.remove(key, semaphore);
    }
  }

}