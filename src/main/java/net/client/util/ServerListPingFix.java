package net.client.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.network.ServerInfo;

/**
 * Reimplementation of the useful behaviour of ServerlistBufferFixer for 1.8.9.
 *
 * <p>Vanilla submits one ping job per rendered server row to a fixed five-thread
 * pool. Repeated refresh clicks can queue an unbounded number of jobs, and a
 * slow / black-holed address can keep a thread occupied for a long time. This
 * class gives the server list a pool sized from the actual server count, caps
 * the number of in-flight jobs, and applies a short timeout so a stuck address
 * fails instead of starving every other row.</p>
 */
public final class ServerListPingFix {
   private static final int MAX_THREADS = 50;
   private static final int PING_TIMEOUT_SECONDS = 4;
   private static final AtomicInteger RUNNING = new AtomicInteger();
   private static final ScheduledExecutorService TIMEOUT = new ScheduledThreadPoolExecutor(
           1, new ThreadFactoryBuilder().setNameFormat("Client Ping Timeout #%d")
                   .setDaemon(true).build());
   private static volatile int serverCount;
   private static volatile int poolSize;
   private static volatile ThreadPoolExecutor executor;

   private ServerListPingFix() {
   }

   /** Called from MultiplayerScreen after the on-disk server list is loaded. */
   public static synchronized void configure(int count) {
      serverCount = Math.max(0, count);
      int wanted = Math.min(MAX_THREADS, Math.max(6, serverCount + 5));
      if (executor == null || poolSize != wanted) {
         // Do not shutdownNow: old jobs are allowed to finish, their daemon
         // threads time out naturally. New jobs go to the correctly sized pool.
         ThreadPoolExecutor old = executor;
         poolSize = wanted;
         ThreadPoolExecutor pool = new ThreadPoolExecutor(
                 wanted, wanted, 60L, TimeUnit.SECONDS,
                 new LinkedBlockingQueue<Runnable>(),
                 new ThreadFactoryBuilder().setNameFormat("Client Server Pinger #%d")
                         .setDaemon(true).build());
         pool.allowCoreThreadTimeOut(true);
         executor = pool;
         // Old jobs finish on their own; do not shutdownNow a list refresh.
      }
   }

   /**
    * Intercepts ServerEntry's {@code SERVER_PINGER_THREAD_POOL.submit(task)}.
    * The passed-in vanilla pool is deliberately ignored so the server list can
    * use the sized pool above without reflecting into a private final field.
    */
   public static Future<?> submit(ThreadPoolExecutor vanillaPool, Runnable task, ServerInfo server) {
      if (task == null) {
         return CompletableFuture.completedFuture(null);
      }
      ThreadPoolExecutor pool = executor;
      if (pool == null) {
         configure(serverCount);
         pool = executor;
      }
      if (pool == null || pool.isShutdown()) {
         fail(server, "\u00a77Ping skipped");
         return CompletableFuture.completedFuture(null);
      }

      int maxRunning = Math.min(80, Math.max(12, serverCount * 2));
      if (RUNNING.get() >= maxRunning) {
         fail(server, "\u00a77Refreshing too fast");
         return CompletableFuture.completedFuture(null);
      }

      RUNNING.incrementAndGet();
      final Future<?> future;
      try {
         future = pool.submit(new Runnable() {
            @Override
            public void run() {
               try {
                  task.run();
               } finally {
                  RUNNING.decrementAndGet();
               }
            }
         });
      } catch (RejectedExecutionException e) {
         RUNNING.decrementAndGet();
         fail(server, "\u00a77Ping skipped");
         return CompletableFuture.completedFuture(null);
      }

      TIMEOUT.schedule(new Runnable() {
         @Override
         public void run() {
            if (!future.isDone()) {
               future.cancel(true);
            }
            if (server.ping < 0L) {
               fail(server, "\u00a7cTimed out");
            }
         }
      }, PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return future;
   }

   private static void fail(ServerInfo server, String label) {
      if (server == null) {
         return;
      }
      server.ping = -1L;
      server.online = true;
      server.label = label;
      server.playerCountLabel = "";
   }
}
