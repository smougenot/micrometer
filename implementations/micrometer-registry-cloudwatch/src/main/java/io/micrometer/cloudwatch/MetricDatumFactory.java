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

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

import static java.util.stream.Collectors.toList;

/**
 * Simple factory to produce AWS SDK MetricDatum
 * Main motivation for this class is to ease the reuse of this logic
 *
 * @author Dawid Kublik
 * @author Jon Schneider
 * @author Johnny Lim
 * @deprecated the micrometer-registry-cloudwatch implementation has been deprecated in favour of
 * micrometer-registry-cloudwatch2, which uses AWS SDK for Java 2.x
 */
@Deprecated
public class MetricDatumFactory {

    private static final Map<String, StandardUnit> STANDARD_UNIT_BY_LOWERCASE_VALUE;

    static {
        Map<String, StandardUnit> standardUnitByLowercaseValue = new HashMap<>();
        for (StandardUnit standardUnit : StandardUnit.values()) {
            standardUnitByLowercaseValue.put(standardUnit.toString().toLowerCase(), standardUnit);
        }
        STANDARD_UNIT_BY_LOWERCASE_VALUE = Collections.unmodifiableMap(standardUnitByLowercaseValue);
    }

    private final Clock clock;
    private final NamingConvention namingConvention;
    private final TimeUnit baseTimeUnit;

    public MetricDatumFactory(Clock clock, NamingConvention namingConvention, TimeUnit baseTimeUnit) {
        this.clock = clock;
        this.namingConvention = namingConvention;
        this.baseTimeUnit = baseTimeUnit;
    }

    @Nullable
    public MetricDatum metricDatum(Meter.Id id, double value) {
        return metricDatum(id, null, id.getBaseUnit(), value);
    }

    @Nullable
    public MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, double value) {
        return metricDatum(id, suffix, id.getBaseUnit(), value);
    }

    @Nullable
    public MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, TimeRelatedValueProducer valueProducer) {
        return metricDatum(id, suffix, id.getBaseUnit(), valueProducer.applyAsDouble(baseTimeUnit));
    }

    @Nullable
    public MetricDatum metricDatumOfTimeUnit(Meter.Id id, @Nullable String suffix, TimeRelatedValueProducer valueProducer) {
        return metricDatum(id, suffix, toStandardUnit(baseTimeUnit.name()), valueProducer.applyAsDouble(baseTimeUnit));
    }

    @Nullable
    public MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, @Nullable String unit, double value) {
        return metricDatum(id, suffix, toStandardUnit(unit), value);
    }

    @Nullable
    public MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, StandardUnit standardUnit, double value) {
        if (Double.isNaN(value)) {
            return null;
        }

        List<Tag> tags = id.getConventionTags(namingConvention);
        return new MetricDatum()
                .withMetricName(getMetricName(id, suffix))
                .withDimensions(toDimensions(tags))
                .withTimestamp(new Date(clock.wallTime()))
                .withValue(CloudWatchUtils.clampMetricValue(value))
                .withUnit(standardUnit);
    }

    // VisibleForTesting
    String getMetricName(Meter.Id id, @Nullable String suffix) {
        String name = suffix != null ? id.getName() + "." + suffix : id.getName();
        return namingConvention.name(name, id.getType(), id.getBaseUnit());
    }

    // VisibleForTesting
    StandardUnit toStandardUnit(@Nullable String unit) {
        if (unit == null) {
            return StandardUnit.None;
        }
        StandardUnit standardUnit = STANDARD_UNIT_BY_LOWERCASE_VALUE.get(unit.toLowerCase());
        return standardUnit != null ? standardUnit : StandardUnit.None;
    }

    // VisibleForTesting
    List<Dimension> toDimensions(List<Tag> tags) {
        return tags.stream()
                .map(tag -> new Dimension().withName(tag.getKey()).withValue(tag.getValue()))
                .collect(toList());
    }

    /**
     * Simple way to provide values adapted to the base time unit of this factory
     */
    public interface TimeRelatedValueProducer extends ToDoubleFunction<TimeUnit> {
    }
}
