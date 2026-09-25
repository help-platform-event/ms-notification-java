package com.maxime.help.msnotification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.maxime.help.msnotification.domain.model.NotificationPreferences;
import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The service end to end against real infrastructure: MySQL and Kafka in containers, and an
 * in-JVM SMTP server (GreenMail) standing in for Mailpit. The test plays ms-auth's part by
 * producing its events as raw JSON, exactly as they appear on the wire.
 */
@Testcontainers
@SpringBootTest(
        properties = {
            "spring.mail.host=localhost",
            "spring.mail.port=3025", // ServerSetupTest.SMTP
            // Short retry delays so the outage scenario reaches the dead-letter topic quickly.
            "app.email.retry.initial-delay-ms=200",
            "app.email.retry.multiplier=1.5"
        })
class NotificationFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    // 4.0.0, not compose.yaml's 3.9.0: see ms-auth-java's CLAUDE.md (3.9.0's entrypoint ignores the
    // advertised-listeners override Testcontainers relies on).
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
            .withPerMethodLifecycle(true);

    private static final String DLT = "notification.email.requested-dlt";

    @Autowired
    RecipientRepository recipientRepository;

    private KafkaProducer<String, String> producer;

    /**
     * ms-auth's topics don't exist in this broker (ms-auth isn't running), so create them before
     * the application's listeners subscribe, as ms-auth would at its own startup.
     */
    @BeforeAll
    static void createAuthTopics() throws Exception {
        try (AdminClient admin =
                AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                            new NewTopic("auth.user.registered", 3, (short) 1),
                            new NewTopic("auth.user.settings-changed", 3, (short) 1),
                            new NewTopic("auth.password.changed", 3, (short) 1)))
                    .all()
                    .get();
        }
    }

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(props);
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    void userRegistered_sendsAWelcomeEmail_andAReplayedEventSendsNothingMore() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "welcome-" + userId + "@example.com";
        String event = userRegistered(UUID.randomUUID(), userId, email, "\"2026-09-25T10:00:00Z\"");

        produce("auth.user.registered", userId, event);

        awaitEmailsTo(email, 1);
        assertThat(subjectsTo(email)).containsExactly("Bienvenue sur H.E.L.P");
        assertThat(recipientRepository.findByUserId(userId))
                .get()
                .extracting(Recipient::getEmail)
                .isEqualTo(java.util.Optional.of(email));

        // Kafka delivers at least once: the very same event again must not send a second email.
        produce("auth.user.registered", userId, event);
        Thread.sleep(3_000);
        assertThat(subjectsTo(email)).hasSize(1);
    }

    @Test
    void legacyEpochTimestamps_areStillAccepted() throws Exception {
        // Events published before ms-auth switched to ISO-8601 stay on the (never-expiring) topic.
        UUID userId = UUID.randomUUID();
        String email = "legacy-" + userId + "@example.com";

        produce("auth.user.registered", userId, userRegistered(UUID.randomUUID(), userId, email, "1790325427.759731730"));

        awaitEmailsTo(email, 1);
    }

    @Test
    void settingsChanged_updatesTheRecipientsPreferences() {
        UUID userId = UUID.randomUUID();
        String event = """
                {"eventId":"%s","occurredAt":"2026-09-25T10:00:00Z","userId":"%s",
                 "availability":{"monday":true,"tuesday":true,"wednesday":true,"thursday":true,
                                 "friday":true,"saturday":false,"sunday":false},
                 "notifications":{"enabled":true,"eventActivity":false,"eventMessages":true,
                                  "documents":true,"deadlines":true,"nearbyEvents":true,"judgments":true}}
                """.formatted(UUID.randomUUID(), userId);

        produce("auth.user.settings-changed", userId, event);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                        recipientRepository.findByUserId(userId))
                .get()
                .extracting(Recipient::getPreferences)
                .isEqualTo(new NotificationPreferences(true, false, true, true, true, true, true)));
    }

    @Test
    void passwordChanged_sendsASecurityEmailToTheKnownAddress() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "security-" + userId + "@example.com";
        produce("auth.user.registered", userId, userRegistered(UUID.randomUUID(), userId, email, "\"2026-09-25T10:00:00Z\""));
        awaitEmailsTo(email, 1);

        produce("auth.password.changed", userId, """
                {"eventId":"%s","occurredAt":"2026-09-25T10:05:00Z","userId":"%s"}
                """.formatted(UUID.randomUUID(), userId));

        awaitEmailsTo(email, 2);
        assertThat(subjectsTo(email)).contains("Votre mot de passe a été modifié");
    }

    @Test
    void anUnparseableEmailRequest_goesStraightToTheDeadLetterTopic() {
        String key = "poison-" + UUID.randomUUID();
        producer.send(new ProducerRecord<>("notification.email.requested", key, "{not json"));

        assertThat(awaitDeadLetter(key).value()).isEqualTo("{not json");
    }

    @Test
    void smtpOutage_retriesThenDeadLetters_theEmailRequest() {
        SMTP.stop();
        UUID userId = UUID.randomUUID();

        produce("auth.user.registered", userId, userRegistered(
                UUID.randomUUID(), userId, "outage-" + userId + "@example.com", "\"2026-09-25T10:00:00Z\""));

        // 4 attempts (main topic + 3 retry topics), then the dead-letter topic, same key.
        ConsumerRecord<String, String> dead = awaitDeadLetter(userId.toString());
        assertThat(dead.value()).contains("outage-" + userId + "@example.com");
    }

    // --- helpers ---

    private static String userRegistered(UUID eventId, UUID userId, String email, String occurredAtJson) {
        return """
                {"eventId":"%s","occurredAt":%s,"userId":"%s","email":"%s"}
                """.formatted(eventId, occurredAtJson, userId, email);
    }

    private void produce(String topic, UUID key, String json) {
        producer.send(new ProducerRecord<>(topic, key.toString(), json));
        producer.flush();
    }

    private void awaitEmailsTo(String address, int count) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(subjectsTo(address)).hasSize(count));
    }

    private List<String> subjectsTo(String address) throws Exception {
        List<String> subjects = new ArrayList<>();
        for (MimeMessage message : SMTP.getReceivedMessagesForDomain(address)) {
            subjects.add(message.getSubject());
        }
        return subjects;
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(DLT));
            Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                });
                assertThat(found).isNotEmpty();
            });
        }
        return found.getFirst();
    }
}
