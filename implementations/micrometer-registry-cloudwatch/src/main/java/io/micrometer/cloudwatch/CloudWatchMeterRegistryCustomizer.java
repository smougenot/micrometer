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

/**
 * Allow to customize some aspects of the registry.
 * @see CloudWatchMeterAdapter
 */
public interface CloudWatchMeterRegistryCustomizer {

    /**
     * Adapts Metrics data to CloudWatch data.
     * Allow to customize the strategy of mapping
     * @param cloudWatchMeterRegistry
     * @return
     */
    CloudWatchMeterAdapter adapter(CloudWatchMeterRegistry cloudWatchMeterRegistry);

    /**
     * Produces AWS SDK type from {@link io.micrometer.core.instrument.Meter}
     * Here to share logic
     * @param cloudWatchMeterRegistry
     * @return
     */
    default MetricDatumFactory metricDatumFactory(CloudWatchMeterRegistry cloudWatchMeterRegistry) {
        return new MetricDatumFactory(
                cloudWatchMeterRegistry.config().clock(),
                cloudWatchMeterRegistry.config().namingConvention(),
                cloudWatchMeterRegistry.getBaseTimeUnit());
    }
}
