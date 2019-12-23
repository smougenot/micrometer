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

import com.amazonaws.AbortedException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link StepMeterRegistry} for Amazon CloudWatch.
 *
 * @author Dawid Kublik
 * @author Jon Schneider
 * @author Johnny Lim
 * @deprecated the micrometer-registry-cloudwatch implementation has been deprecated in favour of
 *             micrometer-registry-cloudwatch2, which uses AWS SDK for Java 2.x
 */
@Deprecated
public class CloudWatchMeterRegistry extends StepMeterRegistry {

    private final CloudWatchConfig config;
    private final AmazonCloudWatchAsync amazonCloudWatchAsync;
    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);
    private final CloudWatchMeterRegistryCustomizer cloudWatchMeterRegistryCustomizer;

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync) {
        this(config, clock, amazonCloudWatchAsync, new NamedThreadFactory("cloudwatch-metrics-publisher"));
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync, ThreadFactory threadFactory) {
        this(config, clock, amazonCloudWatchAsync, threadFactory, defaultCustomizer());
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync, ThreadFactory threadFactory,
                                   CloudWatchMeterRegistryCustomizer cloudWatchMeterRegistryCustomizer) {
        super(config, clock);

        if (config.namespace() == null) {
            throw new MissingRequiredConfigurationException("namespace must be set to report metrics to CloudWatch");
        }

        this.amazonCloudWatchAsync = amazonCloudWatchAsync;
        this.config = config;
        this.cloudWatchMeterRegistryCustomizer = cloudWatchMeterRegistryCustomizer;
        config().namingConvention(NamingConvention.identity);
        start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to cloudwatch every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        boolean interrupted = false;
        try {
            for (List<MetricDatum> batch : MetricDatumPartition.partition(metricData(), config.batchSize())) {
                try {
                    sendMetricData(batch);
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // VisibleForTesting
    void sendMetricData(List<MetricDatum> metricData) throws InterruptedException {
        PutMetricDataRequest putMetricDataRequest = new PutMetricDataRequest()
                .withNamespace(config.namespace())
                .withMetricData(metricData);
        CountDownLatch latch = new CountDownLatch(1);
        amazonCloudWatchAsync.putMetricDataAsync(putMetricDataRequest, new AsyncHandler<PutMetricDataRequest, PutMetricDataResult>() {
            @Override
            public void onError(Exception exception) {
                if (exception instanceof AbortedException) {
                    logger.warn("sending metric data was aborted: {}", exception.getMessage());
                } else {
                    logger.error("error sending metric data.", exception);
                }
                latch.countDown();
            }

            @Override
            public void onSuccess(PutMetricDataRequest request, PutMetricDataResult result) {
                logger.debug("published metric with namespace:{}", request.getNamespace());
                latch.countDown();
            }
        });
        try {
            @SuppressWarnings("deprecation")
            long readTimeoutMillis = config.readTimeout().toMillis();
            latch.await(readTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("metrics push to cloudwatch took longer than expected");
            throw e;
        }
    }

    //VisibleForTesting
    List<MetricDatum> metricData() {
        CloudWatchMeterAdapter batch = cloudWatchMeterRegistryCustomizer.adapter(this);
        return getMeters().stream().flatMap(m -> m.match(
                batch::gaugeData,
                batch::counterData,
                batch::timerData,
                batch::summaryData,
                batch::longTaskTimerData,
                batch::timeGaugeData,
                batch::functionCounterData,
                batch::functionTimerData,
                batch::metricData)
        ).collect(toList());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    //VisibleForTesting
    static CloudWatchMeterRegistryCustomizer defaultCustomizer(){
        return new CloudWatchMeterRegistryCustomizer() {
            @Override
            public CloudWatchMeterAdapter adapter(CloudWatchMeterRegistry cloudWatchMeterRegistry) {
                MetricDatumFactory metricDatumFactory = metricDatumFactory(cloudWatchMeterRegistry);
                return new CloudWatchMeterAdapterDefault(metricDatumFactory);
            }
        };
    }
}
