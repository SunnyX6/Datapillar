package com.sunny.datapillar.openlineage.dao;

import com.sunny.datapillar.openlineage.dao.impl.OpenLineageEventDaoImpl;
import com.sunny.datapillar.openlineage.dao.mapper.OpenLineageEventMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.mock;

class OpenLineageDaoTest {

    private final OpenLineageEventMapper mapper = mock(OpenLineageEventMapper.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final OpenLineageEventDaoImpl dao = new OpenLineageEventDaoImpl(
            mapper,
            new TransactionTemplate(transactionManager),
            120);

    @Test
    void shouldComputeExponentialBackoff() {
        LocalDateTime now = LocalDateTime.of(2026, 2, 28, 0, 0, 0);

        Assertions.assertEquals(now.plusMinutes(1), dao.computeNextRunAt(1, now));
        Assertions.assertEquals(now.plusMinutes(5), dao.computeNextRunAt(2, now));
        Assertions.assertEquals(now.plusMinutes(15), dao.computeNextRunAt(3, now));
        Assertions.assertEquals(now.plusMinutes(60), dao.computeNextRunAt(4, now));
        Assertions.assertEquals(now.plusMinutes(360), dao.computeNextRunAt(10, now));
    }

    @Test
    void shouldClassifyAndTruncateError() {
        Assertions.assertEquals("VALIDATION_ERROR", dao.classifyErrorType(new IllegalArgumentException("bad")));
        Assertions.assertEquals("STATE_ERROR", dao.classifyErrorType(new IllegalStateException("state")));

        RuntimeException longException = new RuntimeException("x".repeat(1200));
        String truncated = dao.truncateError(longException);
        Assertions.assertNotNull(truncated);
        Assertions.assertTrue(truncated.length() <= 1000);
    }

    @Test
    void shouldExposeConfiguredClaimTimeout() {
        Assertions.assertEquals(120L, dao.claimTimeout().toSeconds());
        Assertions.assertTrue(dao.claimTimeout().compareTo(java.time.Duration.ofSeconds(30)) >= 0);
    }

    @Test
    void shouldUseUtcWhenNowMissing() {
        LocalDateTime next = dao.computeNextRunAt(1, null);
        Assertions.assertNotNull(next);
        Assertions.assertTrue(next.isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
    }
}
