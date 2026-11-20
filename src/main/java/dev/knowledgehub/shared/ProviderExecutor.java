package dev.knowledgehub.shared;

import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ProviderExecutor implements AutoCloseable {
    // A timed-out provider cannot create unlimited replacement threads or queued work.
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private final HubSettings settings;
    private final MeterRegistry metrics;
    public ProviderExecutor(HubSettings settings,MeterRegistry metrics) { this.settings=settings; this.metrics=metrics; }
    public <T> T call(String stage,Supplier<T> work) {
        long start=System.nanoTime(); Future<T> task;
        try { task=pool.submit(work::get); }
        catch(RejectedExecutionException e) { throw new HubException(stage+"_BUSY",503); }
        try { return task.get(settings.providerTimeoutSeconds(),TimeUnit.SECONDS); }
        catch(TimeoutException e) { task.cancel(true); metrics.counter("hub.provider.failures","stage",stage).increment(); throw new HubException(stage+"_TIMEOUT",504); }
        catch(InterruptedException e) { task.cancel(true); Thread.currentThread().interrupt(); throw new HubException(stage+"_UNAVAILABLE",503); }
        catch(ExecutionException e) {
            metrics.counter("hub.provider.failures","stage",stage).increment();
            if(e.getCause() instanceof HubException h) throw h;
            throw new HubException(stage+"_UNAVAILABLE",503);
        } finally { metrics.timer("hub.provider.latency","stage",stage).record(System.nanoTime()-start,TimeUnit.NANOSECONDS); }
    }
    public void close() { pool.shutdownNow(); }
}
