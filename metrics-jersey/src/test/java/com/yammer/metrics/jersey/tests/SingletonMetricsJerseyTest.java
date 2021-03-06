package com.yammer.metrics.jersey.tests;

import com.sun.jersey.api.container.MappableContainerException;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.LowLevelAppDescriptor;
import com.yammer.metrics.Meter;
import com.yammer.metrics.MetricRegistry;
import com.yammer.metrics.Timer;
import com.yammer.metrics.jersey.InstrumentedResourceMethodDispatchAdapter;
import com.yammer.metrics.jersey.tests.resources.InstrumentedResource;
import org.junit.Test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yammer.metrics.MetricRegistry.name;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.failBecauseExceptionWasNotThrown;

/**
 * Tests importing {@link InstrumentedResourceMethodDispatchAdapter} as a singleton
 * in a Jersey {@link com.sun.jersey.api.core.ResourceConfig}
 */
public class SingletonMetricsJerseyTest extends JerseyTest {
    static {
        Logger.getLogger("com.sun.jersey").setLevel(Level.OFF);
    }

    private MetricRegistry registry;

    @Override
    protected AppDescriptor configure() {
        this.registry = new MetricRegistry("test");

        final DefaultResourceConfig config = new DefaultResourceConfig();
        config.getSingletons().add(new InstrumentedResourceMethodDispatchAdapter(registry));
        config.getClasses().add(InstrumentedResource.class);

        return new LowLevelAppDescriptor.Builder(config).build();
    }

    @Test
    public void timedMethodsAreTimed() {
        assertThat(resource().path("timed").get(String.class))
                .isEqualTo("yay");

        final Timer timer = registry.timer(name(InstrumentedResource.class, "timed"));

        assertThat(timer.getCount())
                .isEqualTo(1);
    }

    @Test
    public void meteredMethodsAreMetered() {
        assertThat(resource().path("metered").get(String.class))
                .isEqualTo("woo");

        final Meter meter = registry.meter(name(InstrumentedResource.class, "metered"));
        assertThat(meter.getCount())
                .isEqualTo(1);
    }

    @Test
    public void exceptionMeteredMethodsAreExceptionMetered() {
        final Meter meter = registry.meter(name(InstrumentedResource.class,
                                                "exceptionMetered",
                                                "exceptions"));

        assertThat(resource().path("exception-metered").get(String.class))
                .isEqualTo("fuh");

        assertThat(meter.getCount())
                .isZero();
        
        try {
            resource().path("exception-metered").queryParam("splode", "true").get(String.class);
            failBecauseExceptionWasNotThrown(MappableContainerException.class);
        } catch (MappableContainerException e) {
            assertThat(e.getCause())
                    .isInstanceOf(IOException.class);
        }

        assertThat(meter.getCount())
                .isEqualTo(1);
    }
}
