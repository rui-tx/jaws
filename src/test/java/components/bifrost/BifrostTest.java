package components.bifrost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Bifrost;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;

class BifrostTest {

  // --- 1) Empty chain -------------------------------------------------------
  @Test
  @DisplayName("Given empty chain, when execute, then returns true and hasNext false")
  void testGivenEmptyChain_WhenExecute_ThenReturnsTrueAndHasNextFalse() {
    List<Middleware> list = List.of();
    Bifrost chain = new Bifrost(list, null);

    boolean result = chain.execute();

    assertTrue(result);
    assertFalse(chain.hasNext());
    // extra: calling next repeatedly stays true and hasNext stays false
    assertTrue(chain.next());
    assertFalse(chain.hasNext());
  }

  // --- 2) Single middleware happy path -------------------------------------
  @Test
  @DisplayName("Given single middleware calling next, when execute, then returns true and invoked once")
  void testGivenSingleMiddlewareCallsNext_WhenExecute_ThenTrueAndInvokedOnce() {
    TestMiddleware m1 = new TestMiddleware("m1", 1, true, true, Optional.empty());
    Bifrost chain = new Bifrost(List.of(m1), null);

    boolean result = chain.execute();

    assertTrue(result);
    assertEquals(1, m1.getInvokeCount());
    assertFalse(chain.hasNext());
  }

  // --- 3) Single middleware short-circuits without next --------------------
  @Test
  @DisplayName("Given single middleware short-circuits, when execute, then returns false and no next left")
  void testGivenSingleMiddlewareShortCircuits_WhenExecute_ThenFalseAndNoHasNext() {
    TestMiddleware m1 = new TestMiddleware("m1", 0, false, false, Optional.empty());
    Bifrost chain = new Bifrost(List.of(m1), null);

    boolean result = chain.execute();

    assertFalse(result);
    assertFalse(chain.hasNext());
  }

  // --- 4) Multiple middlewares – normal flow -------------------------------
  @Test
  @DisplayName("Given multiple middlewares, when all call next, then order preserved and returns true")
  void testGivenMultipleMiddlewaresNormalFlow_WhenExecute_ThenOrderAndTrue() {
    List<String> order = Collections.synchronizedList(new ArrayList<>());

    Middleware m1 = (ctx, c) -> {
      order.add("m1");
      return c.next();
    };
    Middleware m2 = (ctx, c) -> {
      order.add("m2");
      return c.next();
    };
    Middleware m3 = (ctx, c) -> {
      order.add("m3");
      return c.next();
    };

    Bifrost chain = new Bifrost(List.of(m1, m2, m3), null);

    boolean result = chain.execute();

    assertTrue(result);
    assertEquals(List.of("m1", "m2", "m3"), order);
    assertFalse(chain.hasNext());
  }

  // --- 5) Early false without calling next ---------------------------------
  @Test
  @DisplayName("Given first middleware returns false without next, when execute, then stops and returns false")
  void testGivenEarlyFalse_NoNext_WhenExecute_ThenFalseAndStops() {
    List<String> order = new ArrayList<>();

    Middleware m1 = (ctx, c) -> {
      order.add("m1");
      return false;
    };
    Middleware m2 = (ctx, c) -> {
      order.add("m2");
      return c.next();
    };

    Bifrost chain = new Bifrost(List.of(m1, m2), null);

    boolean result = chain.execute();

    assertFalse(result);
    assertEquals(List.of("m1"), order); // m2 should not run
  }

  // --- 6) First calls next then returns false ------------------------------
  @Test
  @DisplayName("Given first calls next then returns false, when execute, then second runs but final result false")
  void testGivenCallsNextThenReturnsFalse_WhenExecute_ThenM2RunsButFalse() {
    List<String> order = new ArrayList<>();

    Middleware m1 = (ctx, c) -> {
      order.add("m1");
      boolean downstream = c.next();
      return false || downstream; // still false? downstream true, we want final false from m1
    };
    // To ensure final false regardless of downstream, have m1 ignore downstream and return false
    m1 = (ctx, c) -> {
      order.add("m1");
      c.next();
      return false;
    };

    Middleware m2 = (ctx, c) -> {
      order.add("m2");
      return c.next();
    };

    Bifrost chain = new Bifrost(List.of(m1, m2), null);

    boolean result = chain.execute();

    assertFalse(result);
    assertEquals(List.of("m1", "m2"), order);
  }

  // --- 7) Middleware calls next() twice ------------------------------------
  @Test
  @DisplayName("Given middleware calls next twice, when execute, then both downstream middlewares run")
  void testGivenMiddlewareCallsNextTwice_WhenExecute_ThenM2M3Run() {
    List<String> order = new ArrayList<>();

    Middleware m1 = (ctx, c) -> {
      order.add("m1");
      c.next();
      c.next();
      return true;
    };
    Middleware m2 = (ctx, c) -> {
      order.add("m2");
      return c.next();
    };
    Middleware m3 = (ctx, c) -> {
      order.add("m3");
      return c.next();
    };

    Bifrost chain = new Bifrost(List.of(m1, m2, m3), null);

    boolean result = chain.execute();

    assertTrue(result);
    assertEquals(List.of("m1", "m2", "m3"), order);
    assertFalse(chain.hasNext());
  }

  // --- 8) Manual next without next inside middleware -----------------------
  @Test
  @DisplayName("Given first middleware does not call next, when manual next invoked twice, then second runs on second call")
  void testGivenManualNextAdvancement_WhenNextAgain_ThenM2Runs() {
    List<String> order = new ArrayList<>();

    Middleware m1 = (ctx, c) -> {
      order.add("m1");
      return true;
    }; // no c.next()
    Middleware m2 = (ctx, c) -> {
      order.add("m2");
      return c.next();
    };

    Bifrost chain = new Bifrost(List.of(m1, m2), null);

    assertTrue(chain.next()); // handles m1
    assertTrue(chain.hasNext());

    assertTrue(chain.next()); // now handles m2
    assertFalse(chain.hasNext());

    assertEquals(List.of("m1", "m2"), order);
  }

  // --- 9) execute() resets state -------------------------------------------
  @Test
  @DisplayName("Given partial advancement, when execute, then chain resets and runs from start")
  void testGivenExecuteResetsState_WhenCalled_ThenRunsFromStart() {
    List<String> order = new ArrayList<>();

    Middleware m1 = (ctx, c) -> {
      order.add("m1");
      return c.next();
    };
    Middleware m2 = (ctx, c) -> {
      order.add("m2");
      return c.next();
    };

    Bifrost chain = new Bifrost(List.of(m1, m2), null);

    assertTrue(chain.next()); // advance once (m1)
    // m1 calls next() internally, so chain is exhausted after the first call
    assertFalse(chain.hasNext());

    order.clear();
    boolean result = chain.execute(); // should reset to m1 and run m1->m2

    assertTrue(result);
    assertEquals(List.of("m1", "m2"), order);
    assertFalse(chain.hasNext());
  }

  // --- 10) Exceptions propagate --------------------------------------------
  @Test
  @DisplayName("Given middleware throws, when execute, then exception propagates and downstream not invoked")
  void testGivenMiddlewareThrows_WhenExecute_ThenExceptionPropagates() {
    List<String> order = new ArrayList<>();

    Middleware m1 = (ctx, c) -> {
      order.add("m1");
      throw new RuntimeException("boom");
    };
    Middleware m2 = (ctx, c) -> {
      order.add("m2");
      return c.next();
    };

    Bifrost chain = new Bifrost(List.of(m1, m2), null);

    RuntimeException ex = assertThrows(RuntimeException.class, chain::execute);
    assertEquals("boom", ex.getMessage());
    assertEquals(List.of("m1"), order);
  }

  // --- 11) Concurrency: per-request Bifrost instances ----------------------
  @Test
  @DisplayName("Given concurrent per-request Bifrosts, when execute, then all succeed without interference")
  void testGivenConcurrentPerRequestInstances_WhenExecute_ThenNoInterference() throws Exception {
    int threads = 16;

    TestMiddleware tm1 = new TestMiddleware("m1", 1, true, true, Optional.empty());
    TestMiddleware tm2 = new TestMiddleware("m2", 1, true, true, Optional.empty());
    List<Middleware> sharedMiddlewares = List.of(tm1, tm2);

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threads; i++) {
      new Thread(() -> {
        try {
          start.await(1, TimeUnit.SECONDS);
          Bifrost chain = new Bifrost(sharedMiddlewares, null);
          results.add(chain.execute());
        } catch (InterruptedException ignored) {
        } finally {
          done.countDown();
        }
      }).start();
    }

    start.countDown();
    assertTrue(done.await(2, TimeUnit.SECONDS), "threads did not finish in time");

    // all results true
    assertEquals(threads, results.size());
    assertTrue(results.stream().allMatch(Boolean::booleanValue));

    // each middleware called once per thread
    assertEquals(threads, tm1.getInvokeCount());
    assertEquals(threads, tm2.getInvokeCount());
  }

  // --- Test middleware ------------------------------------------------------
  private static class TestMiddleware implements Middleware {

    private final String name;
    private final int timesToCallNext;
    private final boolean followNextResult; // if true, return result of last chain.next(); else return configuredReturn
    private final boolean configuredReturn;
    private final Optional<RuntimeException> throwOnCall;
    private final AtomicInteger invokeCount = new AtomicInteger(0);

    TestMiddleware(String name, int timesToCallNext, boolean followNextResult,
        boolean configuredReturn, Optional<RuntimeException> throwOnCall) {
      this.name = name;
      this.timesToCallNext = timesToCallNext;
      this.followNextResult = followNextResult;
      this.configuredReturn = configuredReturn;
      this.throwOnCall = throwOnCall;
    }

    public int getInvokeCount() {
      return invokeCount.get();
    }

    @Override
    public boolean handle(Yggdrasill.RequestContext context, MiddlewareChain chain) {
      if (throwOnCall.isPresent()) {
        throw throwOnCall.get();
      }
      boolean last = true;
      for (int i = 0; i < timesToCallNext; i++) {
        last = chain.next();
      }
      invokeCount.incrementAndGet();
      return followNextResult ? last : configuredReturn;
    }

    @Override
    public int getOrder() {
      return 100;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
