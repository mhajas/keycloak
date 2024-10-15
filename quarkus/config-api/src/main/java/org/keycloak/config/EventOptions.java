package org.keycloak.config;

public class EventOptions {

    public static final Option<Boolean> USER_EVENT_METRICS_ENABLED = new OptionBuilder<>("user-event-metrics-enabled", Boolean.class)
            .category(OptionCategory.EVENTS)
            .description("Create metrics based on user events.")
            .buildTime(true)
            .defaultValue(Boolean.FALSE)
            .build();

    public static final Option<String> USER_EVENT_METRICS_TAGS = new OptionBuilder<>("user-event-metrics-tags", String.class)
            .category(OptionCategory.EVENTS)
            .description("Comma-separated list of tags to be collected for event metrics. By default only 'realm' is enabled to avoid a high metrics cardinality. Additionally 'clientId' and 'idp' can be specified.")
            .buildTime(false)
            .defaultValue("realm")
            .build();

    public static final Option<String> USER_EVENT_METRICS_EVENTS = new OptionBuilder<>("user-event-metrics-events", String.class)
            .category(OptionCategory.EVENTS)
            .description("Comma-separated list of events to be collected for event metrics. Reduce the number of metrics. If empty or not set, all events create a metric.")
            .buildTime(false)
            .build();

}


