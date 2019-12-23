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
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import io.micrometer.core.instrument.*;

import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * Default implementation is the historical way it was done
 *
 * @author Dawid Kublik
 * @author Jon Schneider
 * @author Johnny Lim
 * @deprecated the micrometer-registry-cloudwatch implementation has been deprecated in favour of
 *             micrometer-registry-cloudwatch2, which uses AWS SDK for Java 2.x
 */
@Deprecated
class CloudWatchMeterAdapterDefault implements CloudWatchMeterAdapter {
    private final MetricDatumFactory metricDatumFactory;

    public CloudWatchMeterAdapterDefault(MetricDatumFactory metricDatumFactory) {
        this.metricDatumFactory = metricDatumFactory;
    }

    @Override
    public Stream<MetricDatum> gaugeData(Gauge gauge) {
        MetricDatum metricDatum = metricDatumFactory.metricDatum(gauge.getId(), "value", gauge.value());
        if (metricDatum == null) {
            return Stream.empty();
        }
        return Stream.of(metricDatum);
    }

    @Override
    public Stream<MetricDatum> counterData(Counter counter) {
        return Stream.of(metricDatumFactory.metricDatum(counter.getId(), "count", StandardUnit.Count, counter.count()));
    }

    @Override
    public Stream<MetricDatum> timerData(Timer timer) {
        Stream.Builder<MetricDatum> metrics = Stream.builder();
        metrics.add(metricDatumFactory.metricDatumOfTimeUnit(timer.getId(), "sum", baseTimeUnit -> timer.totalTime(baseTimeUnit)));
        long count = timer.count();
        metrics.add(metricDatumFactory.metricDatum(timer.getId(), "count", StandardUnit.Count, count));
        if (count > 0) {
            metrics.add(metricDatumFactory.metricDatumOfTimeUnit(timer.getId(), "avg", baseTimeUnit -> timer.mean(baseTimeUnit)));
            metrics.add(metricDatumFactory.metricDatumOfTimeUnit(timer.getId(), "max", baseTimeUnit -> timer.max(baseTimeUnit)));
        }
        return metrics.build();
    }

    @Override
    public Stream<MetricDatum> summaryData(DistributionSummary summary) {
        Stream.Builder<MetricDatum> metrics = Stream.builder();
        metrics.add(metricDatumFactory.metricDatum(summary.getId(), "sum", summary.totalAmount()));
        long count = summary.count();
        metrics.add(metricDatumFactory.metricDatum(summary.getId(), "count", StandardUnit.Count, count));
        if (count > 0) {
            metrics.add(metricDatumFactory.metricDatum(summary.getId(), "avg", summary.mean()));
            metrics.add(metricDatumFactory.metricDatum(summary.getId(), "max", summary.max()));
        }
        return metrics.build();

    }

    @Override
    public Stream<MetricDatum> longTaskTimerData(LongTaskTimer longTaskTimer) {
        return Stream.of(
                metricDatumFactory.metricDatum(longTaskTimer.getId(), "activeTasks", longTaskTimer.activeTasks()),
                metricDatumFactory.metricDatum(longTaskTimer.getId(), "duration", baseTimeUnit -> longTaskTimer.duration(baseTimeUnit)));
    }

    @Override
    public Stream<MetricDatum> timeGaugeData(TimeGauge gauge) {
        MetricDatum metricDatum = metricDatumFactory.metricDatum(gauge.getId(), "value", baseTimeUnit -> gauge.value(baseTimeUnit));
        if (metricDatum == null) {
            return Stream.empty();
        }
        return Stream.of(metricDatum);
    }

    @Override
    public Stream<MetricDatum> functionCounterData(FunctionCounter counter) {
        MetricDatum metricDatum = metricDatumFactory.metricDatum(counter.getId(), "count", StandardUnit.Count, counter.count());
        if (metricDatum == null) {
            return Stream.empty();
        }
        return Stream.of(metricDatum);
    }

    @Override
    public Stream<MetricDatum> functionTimerData(FunctionTimer timer) {
        // we can't know anything about max and percentiles originating from a function timer
        Stream.Builder<MetricDatum> metrics = Stream.builder();
        double count = timer.count();
        metrics.add(metricDatumFactory.metricDatum(timer.getId(), "count", StandardUnit.Count, count));
        if (count > 0) {
            metrics.add(metricDatumFactory.metricDatum(timer.getId(), "avg", baseTimeUnit -> timer.mean(baseTimeUnit)));
        }
        return metrics.build();
    }

    @Override
    public Stream<MetricDatum> metricData(Meter m) {
        return stream(m.measure().spliterator(), false)
                .map(ms -> metricDatumFactory.metricDatum(m.getId().withTag(ms.getStatistic()), ms.getValue()))
                .filter(Objects::nonNull);
    }

}
