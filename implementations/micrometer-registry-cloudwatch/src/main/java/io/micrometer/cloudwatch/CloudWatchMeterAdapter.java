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

import java.util.stream.Stream;

/**
 * Adapt Metric data nature to the AWS SDK data for CloudWatch metrics
 * @deprecated the micrometer-registry-cloudwatch implementation has been deprecated in favour of
 *             micrometer-registry-cloudwatch2, which uses AWS SDK for Java 2.x
 */
public interface CloudWatchMeterAdapter {
    Stream<MetricDatum> gaugeData(Gauge gauge);

    Stream<MetricDatum> counterData(Counter counter);

    Stream<MetricDatum> timerData(Timer timer);

    Stream<MetricDatum> summaryData(DistributionSummary summary);

    Stream<MetricDatum> longTaskTimerData(LongTaskTimer longTaskTimer);

    Stream<MetricDatum> timeGaugeData(TimeGauge gauge);

    Stream<MetricDatum> functionCounterData(FunctionCounter counter);

    Stream<MetricDatum> functionTimerData(FunctionTimer timer);

    Stream<MetricDatum> metricData(Meter m);
}
