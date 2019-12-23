/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.cloudwatch;

import com.amazonaws.services.cloudwatch.model.MetricDatum;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static io.micrometer.core.instrument.Meter.Id;
import static io.micrometer.core.instrument.Meter.Type;
import static io.micrometer.core.instrument.Meter.Type.DISTRIBUTION_SUMMARY;
import static io.micrometer.core.instrument.Meter.Type.TIMER;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CloudWatchMeterRegistry}.
 *
 * @author Johnny Lim
 */
@SuppressWarnings("deprecation")
class CloudWatchMeterRegistryTest {
    private static final String METER_NAME = "test";
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
    private final CloudWatchMeterAdapter registryBatch = CloudWatchMeterRegistry.defaultCustomizer().adapter(registry);

    @Test
    void metricData() {
        registry.gauge("gauge", 1d);
        List<MetricDatum> metricDatumStream = registry.metricData();
        assertThat(metricDatumStream.size()).isEqualTo(1);
    }

    @Test
    void metricDataWhenNaNShouldNotAdd() {
        registry.gauge("gauge", Double.NaN);

        AtomicReference<Double> value = new AtomicReference<>(Double.NaN);
        registry.more().timeGauge("time.gauge", Tags.empty(), value, TimeUnit.MILLISECONDS, AtomicReference::get);

        List<MetricDatum> metricDatumStream = registry.metricData();
        assertThat(metricDatumStream.size()).isEqualTo(0);
    }

    @Test
    void batchFunctionCounterData() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registryBatch.functionCounterData(counter)).hasSize(1);
    }

    @Test
    void batchFunctionCounterDataShouldClampInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("my.positive.infinity", Double.POSITIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registryBatch.functionCounterData(counter).findFirst().get().getValue())
                .isEqualTo(CloudWatchUtils.MAXIMUM_ALLOWED_VALUE);

        counter = FunctionCounter.builder("my.negative.infinity", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registryBatch.functionCounterData(counter).findFirst().get().getValue())
                .isEqualTo(-CloudWatchUtils.MAXIMUM_ALLOWED_VALUE);
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNaNValuesShouldNotBeWritten() {
        Measurement measurement = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registryBatch.metricData(meter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedNaNAndNonNaNValuesShouldSkipOnlyNaNValues() {
        Measurement measurement1 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registryBatch.metricData(meter)).hasSize(2);
    }

    @Test
    void shouldAddFunctionTimerAggregateMetricWhenAtLeastOneEventHappened() {
        FunctionTimer timer = mock(FunctionTimer.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, TIMER);
        when(timer.getId()).thenReturn(meterId);
        when(timer.count()).thenReturn(2.0);

        Stream<MetricDatum> metricDatumStream = registryBatch.functionTimerData(timer);

        assertThat(metricDatumStream.anyMatch(hasAvgMetric(meterId))).isTrue();
    }

    @Test
    void shouldNotAddFunctionTimerAggregateMetricWhenNoEventHappened() {
        FunctionTimer timer = mock(FunctionTimer.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, TIMER);
        when(timer.getId()).thenReturn(meterId);
        when(timer.count()).thenReturn(0.0);

        Stream<MetricDatum> metricDatumStream = registryBatch.functionTimerData(timer);

        assertThat(metricDatumStream.noneMatch(hasAvgMetric(meterId))).isTrue();
    }

    @Test
    void shouldAddTimerAggregateMetricWhenAtLeastOneEventHappened() {
        Timer timer = mock(Timer.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, TIMER);
        when(timer.getId()).thenReturn(meterId);
        when(timer.count()).thenReturn(2L);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.timerData(timer);

        assertThat(streamSupplier.get().anyMatch(hasAvgMetric(meterId))).isTrue();
        assertThat(streamSupplier.get().anyMatch(hasMaxMetric(meterId))).isTrue();
    }

    @Test
    void shouldNotAddTimerAggregateMetricWhenNoEventHappened() {
        Timer timer = mock(Timer.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, TIMER);
        when(timer.getId()).thenReturn(meterId);
        when(timer.count()).thenReturn(0L);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.timerData(timer);

        assertThat(streamSupplier.get().noneMatch(hasAvgMetric(meterId))).isTrue();
        assertThat(streamSupplier.get().noneMatch(hasMaxMetric(meterId))).isTrue();
    }

    @Test
    void shouldAddDistributionSumAggregateMetricWhenAtLeastOneEventHappened() {
        DistributionSummary summary = mock(DistributionSummary.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, DISTRIBUTION_SUMMARY);
        when(summary.getId()).thenReturn(meterId);
        when(summary.count()).thenReturn(2L);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.summaryData(summary);

        assertThat(streamSupplier.get().anyMatch(hasAvgMetric(meterId))).isTrue();
        assertThat(streamSupplier.get().anyMatch(hasMaxMetric(meterId))).isTrue();
    }

    @Test
    void shouldNotAddDistributionSumAggregateMetricWhenNoEventHappened() {
        DistributionSummary summary = mock(DistributionSummary.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, DISTRIBUTION_SUMMARY);
        when(summary.getId()).thenReturn(meterId);
        when(summary.count()).thenReturn(0L);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.summaryData(summary);

        assertThat(streamSupplier.get().noneMatch(hasAvgMetric(meterId))).isTrue();
        assertThat(streamSupplier.get().noneMatch(hasMaxMetric(meterId))).isTrue();
    }

    @Test
    public void batchSizeShouldWorkOnMetricDatum() throws InterruptedException {
        List<Meter> meters = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Timer timer = Timer.builder("timer." + i).register(this.registry);
            meters.add(timer);
        }
        when(this.registry.getMeters()).thenReturn(meters);
        doNothing().when(this.registry).sendMetricData(any());
        this.registry.publish();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MetricDatum>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.registry, times(2)).sendMetricData(argumentCaptor.capture());
        List<List<MetricDatum>> allValues = argumentCaptor.getAllValues();
        assertThat(allValues.get(0)).hasSize(20);
        assertThat(allValues.get(1)).hasSize(20);
    }

    private Predicate<MetricDatum> hasAvgMetric(Id id) {
        return e -> e.getMetricName().equals(id.getName().concat(".avg"));
    }

    private Predicate<MetricDatum> hasMaxMetric(Id id) {
        return e -> e.getMetricName().equals(id.getName().concat(".max"));
    }
}
