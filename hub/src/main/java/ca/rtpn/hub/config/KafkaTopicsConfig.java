package ca.rtpn.hub.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic layout. payments.inbound is keyed by debtor participant so that all
 * payments from one institution land on the same partition and are processed
 * in order — the property that makes the sliding-window velocity check and
 * per-debtor liquidity behaviour deterministic.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic inboundTopic(@Value("${rtpn.topics.inbound}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic clearedTopic(@Value("${rtpn.topics.cleared}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rejectedTopic(@Value("${rtpn.topics.rejected}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
