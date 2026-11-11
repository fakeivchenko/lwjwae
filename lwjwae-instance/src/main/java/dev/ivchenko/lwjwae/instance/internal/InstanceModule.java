package dev.ivchenko.lwjwae.instance.internal;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.Window;
import dev.ivchenko.lwjwae.event.EventSubscription;
import dev.ivchenko.lwjwae.instance.SecondInstanceEvent;
import dev.ivchenko.lwjwae.module.ApplicationModule;
import dev.ivchenko.lwjwae.module.ModuleContext;
import dev.ivchenko.lwjwae.util.OpenArgumentUtil;
import dev.ivchenko.lwjwae.util.ThrowableUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The single instance of one application: the lock that it holds once {@link
 * dev.ivchenko.lwjwae.instance.SingleInstance#create} claimed it, and the listeners of the second
 * starts.
 *
 * <p>A start that comes before any listener waits in a list, so the starts that race the setup of
 * the application aren't lost; the first listener hears them on the thread that registers it, and
 * every later start reaches the listeners on the thread of the lock, in the order of registration,
 * while the process that started waits.
 */
public final class InstanceModule implements ApplicationModule {
  private final Map<Long, Consumer<SecondInstanceEvent>> listeners = new ConcurrentSkipListMap<>();
  private final AtomicLong listenerIds = new AtomicLong();
  private final List<SecondInstanceEvent> unheard = new ArrayList<>();
  private ModuleContext context;
  private volatile InstanceLock lock;

  @Override
  public void start(ModuleContext context) {
    this.context = context;
  }

  /** Takes over {@code lock}, and answers the processes that start after it, until the end. */
  public void serve(InstanceLock lock) {
    this.lock = lock;
    lock.serve(this::started);
    if (this.context.application().isClosed()) {
      lock.close();
    }
  }

  /** Listens to the second starts, see {@link dev.ivchenko.lwjwae.instance.SingleInstance}. */
  public EventSubscription onSecondInstance(Consumer<SecondInstanceEvent> listener) {
    while (true) {
      List<SecondInstanceEvent> held;
      synchronized (this.unheard) {
        if (this.unheard.isEmpty()) {
          long id = this.listenerIds.incrementAndGet();
          this.listeners.put(id, listener);
          return () -> this.listeners.remove(id);
        }
        held = List.copyOf(this.unheard);
        this.unheard.clear();
      }
      held.forEach(start -> InstanceModule.tell(listener, start));
    }
  }

  @Override
  public void stop() {
    InstanceLock held = this.lock;
    if (held != null) {
      held.close();
    }
  }

  /**
   * Another process of the application started and handed its start over: the oldest window comes
   * to the front, and the listeners hear of it, and of the links and the files among its arguments.
   */
  private void started(SecondInstanceEvent start) {
    this.context.openRequested(OpenArgumentUtil.of(start.arguments(), start.workingDirectory()));
    Application application = this.context.application();
    List<Window> open = application.windows();
    if (!open.isEmpty()) {
      try {
        open.getFirst().focus();
      } catch (Throwable t) {
        ThrowableUtil.report(t);
      }
    }
    synchronized (this.unheard) {
      if (this.listeners.isEmpty()) {
        this.unheard.add(start);
        return;
      }
    }
    this.listeners.values().forEach(listener -> InstanceModule.tell(listener, start));
  }

  private static void tell(Consumer<SecondInstanceEvent> listener, SecondInstanceEvent start) {
    try {
      listener.accept(start);
    } catch (Throwable t) {
      ThrowableUtil.report(t);
    }
  }
}
