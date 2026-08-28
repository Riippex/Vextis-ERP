package com.vextis.livesession.infrastructure.persistence;

import com.vextis.livesession.application.CreateLiveSessionCommand;
import com.vextis.livesession.application.LiveSessionQuotaExceededException;
import com.vextis.livesession.application.LiveSessionService;
import com.vextis.livesession.application.port.LiveSessionRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the actor session quota is atomic under real concurrent load against
 * a real Postgres instance — something the single-threaded
 * {@code LiveSessionServiceTests} cannot do, because its in-memory fake never
 * reproduces the check-then-act race a shared connection pool exposes.
 *
 * <p>{@link LiveSessionService#create} runs {@code enforceActorQuota} (lock,
 * then count, then the eventual insert) inside one {@code @Transactional}
 * method. Without a real transaction manager bound to a real connection pool,
 * {@code pg_advisory_xact_lock} would be acquired and released by itself on a
 * borrowed-and-returned connection before the count even runs, so this test
 * wires an actual {@link DataSourceTransactionManager} rather than calling the
 * repository directly.
 *
 * <p>Needs Docker; {@link EnabledIfDockerAvailable} reports the class as
 * disabled rather than failed when the daemon is unreachable, so it does not
 * break {@code ./gradlew test} on a machine without one.
 */
@Testcontainers
@EnabledIfDockerAvailable
class LiveSessionServiceConcurrencyTests {

    private static final int QUOTA = 3;
    private static final int CONCURRENT_ATTEMPTS = 20;

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("vextis_live_quota_test")
            .withUsername("vextis")
            .withPassword("vextis_test_only");

    /**
     * Explicit {@code @Bean} wiring rather than component scanning: this test
     * lives in the same package as the package-private
     * {@link JdbcLiveSessionRepository} deliberately, so it can be constructed
     * directly without widening its visibility for a test's sake, and
     * {@code @Transactional} on {@link LiveSessionService#create} only takes
     * effect on beans this context actually proxies.
     */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl(POSTGRES.getJdbcUrl());
            dataSource.setUsername(POSTGRES.getUsername());
            dataSource.setPassword(POSTGRES.getPassword());
            return dataSource;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        NamedParameterJdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        LiveSessionRepository liveSessionRepository(NamedParameterJdbcTemplate jdbc) {
            return new JdbcLiveSessionRepository(jdbc);
        }

        @Bean
        LiveSessionService liveSessionService(LiveSessionRepository repository) {
            return new LiveSessionService(
                    repository, Clock.system(ZoneOffset.UTC), "wss://agent-runtime.example.com",
                    QUOTA, Duration.ofHours(1));
        }
    }

    @Test
    void concurrentCreatesForOneActorNeverExceedTheQuota() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            Flyway.configure()
                    .dataSource(context.getBean(DataSource.class))
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            LiveSessionService service = context.getBean(LiveSessionService.class);
            LiveSessionRepository repository = context.getBean(LiveSessionRepository.class);

            String tenantId = "demo-tenant";
            String actorId = "firebase-user-concurrent";

            AtomicInteger succeeded = new AtomicInteger();
            AtomicInteger refused = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
            // Every worker blocks here so the create() calls actually collide
            // instead of trickling in one at a time, which is what would make
            // this test pass even without the fix.
            CountDownLatch allWorkersReady = new CountDownLatch(CONCURRENT_ATTEMPTS);
            CountDownLatch releaseAllAtOnce = new CountDownLatch(1);

            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < CONCURRENT_ATTEMPTS; i++) {
                    futures.add(pool.submit(() -> {
                        allWorkersReady.countDown();
                        try {
                            releaseAllAtOnce.await(10, TimeUnit.SECONDS);
                            service.create(new CreateLiveSessionCommand(tenantId, actorId, UUID.randomUUID()));
                            succeeded.incrementAndGet();
                        } catch (LiveSessionQuotaExceededException expected) {
                            refused.incrementAndGet();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }));
                }

                assertThat(allWorkersReady.await(10, TimeUnit.SECONDS)).isTrue();
                releaseAllAtOnce.countDown();
                for (Future<?> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdown();
            }

            assertThat(succeeded.get())
                    .as("exactly the quota must succeed under %d simultaneous attempts, never more", CONCURRENT_ATTEMPTS)
                    .isEqualTo(QUOTA);
            assertThat(refused.get()).isEqualTo(CONCURRENT_ATTEMPTS - QUOTA);
            assertThat(repository.countCreatedSince(tenantId, actorId, Instant.EPOCH))
                    .as("the row count in the database must agree with what create() reported")
                    .isEqualTo(QUOTA);
        }
    }

    @Test
    void concurrentCreatesForDifferentActorsAreNotSerializedAgainstEachOther() throws Exception {
        // The advisory lock key is scoped to (tenant, actor); this guards
        // against a key collision that would make unrelated actors block each
        // other or, worse, share a quota they should not.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            Flyway.configure()
                    .dataSource(context.getBean(DataSource.class))
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            LiveSessionService service = context.getBean(LiveSessionService.class);
            String tenantId = "demo-tenant";
            int actorCount = QUOTA + 5;

            ExecutorService pool = Executors.newFixedThreadPool(actorCount);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < actorCount; i++) {
                    String actorId = "firebase-user-" + i;
                    futures.add(pool.submit(() ->
                            service.create(new CreateLiveSessionCommand(tenantId, actorId, UUID.randomUUID()))));
                }
                for (Future<?> future : futures) {
                    // Every actor is under their own individual quota, so none
                    // of these should throw LiveSessionQuotaExceededException.
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdown();
            }
        }
    }
}
