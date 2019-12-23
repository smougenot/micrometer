package io.micrometer.cloudwatch;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MetricDatumFactoryTest {

    private final MetricDatumFactory metricDatumFactory = new MetricDatumFactory(Clock.SYSTEM, NamingConvention.slashes, TimeUnit.MILLISECONDS);
    private final Meter.Id id = new Meter.Id("name", Tags.empty(), null, null, Meter.Type.COUNTER);

    @Test
    void metricDataWhenNaNShouldNotProduceMetricDatum() {
        assertThat(metricDatumFactory.metricDatum(id, null, id.getBaseUnit(), Double.NaN)).isNull();
    }

    @Test
    void getMetricNameShouldAppendSuffix() {
        assertThat(metricDatumFactory.getMetricName(id, "suffix")).isEqualTo("name/suffix");
    }

    @Test
    void getMetricNameWhenSuffixIsNullShouldNotAppend() {
        assertThat(metricDatumFactory.getMetricName(id, null)).isEqualTo("name");
    }

    @Test
    void toStandardUnitShouldFindUnitLowerCased() {
        assertThat(metricDatumFactory.toStandardUnit(StandardUnit.GigabitsSecond.toString().toLowerCase())).isEqualTo(StandardUnit.GigabitsSecond);
    }

    @Test
    void toStandardUnitShouldFindUnitAnyCased() {
        assertThat(metricDatumFactory.toStandardUnit("TeRaBIts/seCoND")).isEqualTo(StandardUnit.TerabitsSecond);
    }

    @Test
    void toStandardUnitShouldUseDefaultOnNotFound() {
        assertThat(metricDatumFactory.toStandardUnit("toto")).isEqualTo(StandardUnit.None);
    }

    @Test
    void toStandardUnitShouldUseDefaultOnNull() {
        assertThat(metricDatumFactory.toStandardUnit(null)).isEqualTo(StandardUnit.None);
    }

    @Test
    void toDimensionsShouldReturnEmptyCollectionOnEmptyTags() {
        assertThat(metricDatumFactory.toDimensions(Collections.emptyList())).isEmpty();
    }

    @Test
    void toDimensionsReturnShouldHaveTheSameSizeAsTags() {
        List<Tag> tags = Arrays.asList(
                Tag.of("ak", "av"),
                Tag.of("bk", "bv"),
                Tag.of("ck", "cv")
        );
        assertThat(metricDatumFactory.toDimensions(tags)).hasSize(tags.size());
    }

    @Test
    void toDimensionsShouldConvertOneTagToOneDimension() {
        String key = "dk";
        String value = "dv";
        assertThat(metricDatumFactory.toDimensions(Collections.singletonList(Tag.of(key, value))))
                .containsExactly(new Dimension().withName(key).withValue(value));
    }

}
