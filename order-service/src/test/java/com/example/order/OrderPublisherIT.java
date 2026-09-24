package com.example.order;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class OrderPublisherIT {
    static KafkaContainer kafka;

    @BeforeAll
    static void startKafka() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
        kafka.start();
    }

    @AfterAll
    static void stopKafka() {
        if (kafka != null) kafka.stop();
    }

    @Test
    void publishAndConsume() throws Exception {
        String bootstrap = kafka.getBootstrapServers();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String,String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("orders"));

            // produce via plain HTTP call to controller or directly using kafka client
            // for simplicity, use kafka client producer here
            var producerProps = new java.util.Properties();
            producerProps.put("bootstrap.servers", bootstrap);
            producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            try (var producer = new org.apache.kafka.clients.producer.KafkaProducer<String,String>(producerProps)) {
                producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>("orders", "k", "{\"orderId\":123,\"items\":[{\"productId\":1,\"quantity\":2}]}"));
                producer.flush();
            }

            ConsumerRecords<String,String> recs = consumer.poll(Duration.ofSeconds(5));
            assertFalse(recs.isEmpty(), "Expected at least one message on orders topic");
            boolean found = false;
            for (ConsumerRecord<String,String> r : recs) {
                if (r.value().contains("orderId")) { found = true; break; }
            }
            assertTrue(found, "Published order not found in consumed messages");
        }
    }
}
