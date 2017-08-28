package application.servlet;

import com.spotify.dns.statistics.DnsReporter;
import com.spotify.dns.statistics.DnsTimingContext;

public class StdoutReporter implements DnsReporter {
    @Override
    public DnsTimingContext resolveTimer() {
        return new DnsTimingContext() {
            private final long start = System.currentTimeMillis();

            @Override
            public void stop() {
                final long now = System.currentTimeMillis();
                final long diff = now - start;
                System.out.println("Request took " + diff + "ms");
            }
        };
    }

    @Override
    public void reportFailure(Throwable error) {
        System.err.println("Error when resolving: " + error);
        error.printStackTrace(System.err);
    }

    @Override
    public void reportEmpty() {
        System.out.println("Empty response from server.");
    }
}
