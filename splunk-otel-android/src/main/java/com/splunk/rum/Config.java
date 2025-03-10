/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.splunk.rum;

import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

/**
 * Configuration class for the Splunk Android RUM (Real User Monitoring) library.
 * <p>
 * Both the beaconUrl and the rumAuthToken are mandatory configuration settings. Trying
 * to build a Config instance without both of these items specified will result in an exception being thrown.
 */
public class Config {

    private final String beaconEndpoint;
    private final String rumAccessToken;
    private final boolean debugEnabled;
    private final String applicationName;
    private final boolean crashReportingEnabled;
    private final boolean networkMonitorEnabled;
    private final boolean anrDetectionEnabled;
    private final AtomicReference<Attributes> globalAttributes = new AtomicReference<>();
    private final Function<SpanExporter, SpanExporter> spanFilterExporterDecorator;

    private Config(Builder builder) {
        this.beaconEndpoint = builder.beaconEndpoint;
        this.rumAccessToken = builder.rumAccessToken;
        this.debugEnabled = builder.debugEnabled;
        this.applicationName = builder.applicationName;
        this.crashReportingEnabled = builder.crashReportingEnabled;
        this.globalAttributes.set(addDeploymentEnvironment(builder));
        this.networkMonitorEnabled = builder.networkMonitorEnabled;
        this.anrDetectionEnabled = builder.anrDetectionEnabled;
        this.spanFilterExporterDecorator = builder.spanFilterBuilder.build();
    }

    private Attributes addDeploymentEnvironment(Builder builder) {
        Attributes globalAttributes = builder.globalAttributes;
        if (builder.deploymentEnvironment != null) {
            globalAttributes = globalAttributes.toBuilder()
                    .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, builder.deploymentEnvironment)
                    .build();
        }
        return globalAttributes;
    }

    /**
     * The configured "beacon" URL for the RUM library.
     */
    public String getBeaconEndpoint() {
        return beaconEndpoint;
    }

    /**
     * The configured RUM access token for the library.
     */
    public String getRumAccessToken() {
        return rumAccessToken;
    }

    /**
     * Is debug mode enabled.
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * The name under which this application will be reported to the Splunk RUM system.
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Is the crash-reporting feature enabled or not.
     */
    public boolean isCrashReportingEnabled() {
        return crashReportingEnabled;
    }

    /**
     * The set of {@link Attributes} which will be applied to every span generated by the RUM
     * instrumentation.
     */
    public Attributes getGlobalAttributes() {
        return globalAttributes.get();
    }

    /**
     * Create a new instance of the {@link Builder} class. All default configuration options will be pre-populated.
     */
    public static Builder builder() {
        return new Builder();
    }

    public boolean isNetworkMonitorEnabled() {
        return networkMonitorEnabled;
    }

    public boolean isAnrDetectionEnabled() {
        return anrDetectionEnabled;
    }

    void updateGlobalAttributes(Consumer<AttributesBuilder> updater) {
        AttributesBuilder builder = globalAttributes.get().toBuilder();
        updater.accept(builder);
        globalAttributes.set(builder.build());
    }

    SpanExporter decorateWithSpanFilter(SpanExporter exporter) {
        return spanFilterExporterDecorator.apply(exporter);
    }

    /**
     * Builder class for the Splunk RUM {@link Config} class.
     */
    public static class Builder {
        public boolean networkMonitorEnabled = true;
        public boolean anrDetectionEnabled = true;
        private String beaconEndpoint;
        private String rumAccessToken;
        private boolean debugEnabled = false;
        private String applicationName;
        private boolean crashReportingEnabled = true;
        private Attributes globalAttributes = Attributes.empty();
        private String deploymentEnvironment;
        private final SpanFilterBuilder spanFilterBuilder = new SpanFilterBuilder();
        private String realm;

        /**
         * Create a new instance of {@link Config} from the options provided.
         */
        public Config build() {
            if (rumAccessToken == null || beaconEndpoint == null || applicationName == null) {
                throw new IllegalStateException("You must provide a rumAccessToken, a realm (or full beaconEndpoint), and an applicationName to create a valid Config instance.");
            }
            return new Config(this);
        }

        /**
         * Assign the "beacon" endpoint URL to be used by the RUM library.
         * <p>
         * Note that if you are using standard Splunk ingest, it is simpler to just use {@link #realm(String)}
         * and let this configuration set the full endpoint URL for you.
         *
         * @return this
         */
        public Builder beaconEndpoint(String beaconEndpoint) {
            if (realm != null) {
                Log.w(SplunkRum.LOG_TAG, "Explicitly setting the beaconEndpoint will override the realm configuration.");
                realm = null;
            }
            this.beaconEndpoint = beaconEndpoint;
            return this;
        }

        /**
         * Sets the realm for the beacon to send RUM telemetry to. This should be used in place
         * of the {@link #beaconEndpoint(String)} method in most cases.
         *
         * @param realm A valid Splunk "realm"
         * @return this
         */
        public Builder realm(String realm) {
            if (beaconEndpoint != null && this.realm == null) {
                Log.w(SplunkRum.LOG_TAG, "beaconEndpoint has already been set. Realm configuration will be ignored.");
                return this;
            }
            this.beaconEndpoint = "https://rum-ingest." + realm + ".signalfx.com/v1/rum";
            this.realm = realm;
            return this;
        }

        /**
         * Assign the RUM auth token to be used by the RUM library.
         *
         * @return this
         */
        public Builder rumAccessToken(String rumAuthToken) {
            this.rumAccessToken = rumAuthToken;
            return this;
        }

        /**
         * Enable/disable debugging information to be emitted from the RUM library. This is set to
         * {@code false} by default.
         *
         * @return this
         */
        public Builder debugEnabled(boolean enable) {
            this.debugEnabled = enable;
            return this;
        }

        /**
         * Enable/disable the crash reporting feature. Enabled by default.
         *
         * @return this
         */
        public Builder crashReportingEnabled(boolean enable) {
            this.crashReportingEnabled = enable;
            return this;
        }

        /**
         * Enable/disable the network monitoring feature. Enabled by default.
         *
         * @return this
         */
        public Builder networkMonitorEnabled(boolean enable) {
            this.networkMonitorEnabled = enable;
            return this;
        }

        /**
         * Assign an application name that will be used to identify your application in the Splunk RUM UI.
         *
         * @return this.
         */
        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        /**
         * Enable/disable the ANR detection feature. Enabled by default. If enabled, if the main
         * thread is unresponsive for 5s or more, an event including the main thread's stack trace will be
         * reported to the RUM system.
         *
         * @return this.
         */
        public Builder anrDetectionEnabled(boolean enable) {
            this.anrDetectionEnabled = enable;
            return this;
        }

        /**
         * Provide a set of global {@link Attributes} that will be applied to every span generated
         * by the RUM instrumentation.
         *
         * @return this.
         */
        public Builder globalAttributes(Attributes attributes) {
            this.globalAttributes = attributes == null ? Attributes.empty() : attributes;
            return this;
        }

        /**
         * Assign the deployment environment for this RUM instance. Will be passed along as a span
         * attribute to help identify in the Splunk RUM UI.
         *
         * @param environment The deployment environment name.
         * @return this.
         */
        public Builder deploymentEnvironment(String environment) {
            this.deploymentEnvironment = environment;
            return this;
        }

        /**
         * Configure span data filtering.
         *
         * @param configurer A function that will configure the passed {@link SpanFilterBuilder}.
         * @return {@code this}.
         */
        public Builder filterSpans(Consumer<SpanFilterBuilder> configurer) {
            configurer.accept(spanFilterBuilder);
            return this;
        }
    }
}
