package io.micrometer.cloudwatch;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

class CloudWatchMeterAdapterDefaultTest {

    private final CloudWatchConfig config = new CloudWatchConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String namespace() {
            return "namespace";
        }
    };

    private final MockClock clock = new MockClock();
    private final CloudWatchMeterRegistry registry = spy(new CloudWatchMeterRegistry(config, clock, null));
    private final CloudWatchMeterAdapterDefault cloudWatchMeterAdapterDefault = new CloudWatchMeterAdapterDefault(new MetricDatumFactory(
            Clock.SYSTEM, NamingConvention.slashes, TimeUnit.MILLISECONDS));
    @Test
    void gaugeData() {
    }

    @Test
    void counterData() {
    }

    @Test
    void timerData() {
    }

    @Test
    void summaryData() {
    }

    @Test
    void longTaskTimerData() {
    }

    @Test
    void timeGaugeData() {
    }


    @Test
    void batchFunctionCounterData() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue)
                .register(registry);
        clock.add(config.step());
        assertThat(cloudWatchMeterAdapterDefault.functionCounterData(counter)).hasSize(1);
    }

    @Test
    void batchFunctionCounterDataShouldClampInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("my.positive.infinity", Double.POSITIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(cloudWatchMeterAdapterDefault.functionCounterData(counter).findFirst().get().getValue())
                .isEqualTo(CloudWatchUtils.MAXIMUM_ALLOWED_VALUE);

        counter = FunctionCounter.builder("my.negative.infinity", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(cloudWatchMeterAdapterDefault.functionCounterData(counter).findFirst().get().getValue())
                .isEqualTo(-CloudWatchUtils.MAXIMUM_ALLOWED_VALUE);
    }

    @Test
    void functionTimerData() {
    }

    @Test
    void metricData() {
    }
}
