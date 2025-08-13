package org.ruitx.jaws.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.ruitx.jaws.utils.logger.JawsLogger;

/**
 * Norns is a service that manages scheduled tasks like a cron job.
 */
public class Norns implements Runnable {

  private static Norns instance;
  private final Map<String, CronTask> tasks;
  private volatile boolean running = true;

  private Norns() {
    this.tasks = new ConcurrentHashMap<>();
  }

  /**
   * Returns the singleton instance of Norns.
   *
   * @return the Norns instance
   */
  public static synchronized Norns getInstance() {
    if (instance == null) {
      instance = new Norns();
    }
    return instance;
  }

  /**
   * Registers a task with a name, runnable, interval, and time unit.
   *
   * @param name     the name of the task
   * @param task     the runnable task to execute
   * @param interval the interval at which to run the task
   * @param unit     the time unit for the interval
   */
  public void registerTask(String name, Runnable task, long interval, TimeUnit unit) {
    long intervalMillis = unit.toMillis(interval);
    tasks.put(name, new CronTask(task, intervalMillis));
    JawsLogger.info("Registered task with Norns: {} with interval: {} {}", name, interval, unit);
  }

  /**
   * Unregisters a task by its name.
   *
   * @param name the name of the task to unregister
   */
  public void unregisterTask(String name) {
    tasks.remove(name);
    JawsLogger.info("Unregistered task from Norns: {}", name);
  }

  @Override
  public void run() {
    JawsLogger.info("Norns started weaving the threads of fate");
    while (running) {
      try {
        long now = System.currentTimeMillis();
        tasks.forEach((name, task) -> {
          if (task.shouldRun(now)) {
            try {
              task.run();
              task.updateLastRun(now);
            } catch (Exception e) {
              JawsLogger.error("Error in Norns' thread {}: {}", name, e.getMessage(), e);
            }
          }
        });
        TimeUnit.SECONDS.sleep(1); // Check tasks every second
      } catch (InterruptedException e) {
        JawsLogger.info("Norns interrupted, ceasing their weaving");
        running = false;
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        JawsLogger.error("Error in Norns: {}", e.getMessage(), e);
      }
    }
    JawsLogger.info("Norns have ceased their weaving");
  }

  /**
   * Stops the Norns service.
   */
  public void stop() {
    running = false;
  }

  private static class CronTask {

    private final Runnable task;
    private final long intervalMillis;
    private long lastRun;

    public CronTask(Runnable task, long intervalMillis) {
      this.task = task;
      this.intervalMillis = intervalMillis;
      this.lastRun = System.currentTimeMillis();
    }

    public boolean shouldRun(long now) {
      return now - lastRun >= intervalMillis;
    }

    public void run() {
      task.run();
    }

    public void updateLastRun(long time) {
      this.lastRun = time;
    }
  }
} 