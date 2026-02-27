package com.sunny.datapillar.common.exception.db;

import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConstraintNameExtractorTest {

    @Test
    void extract_shouldResolveMySqlKeyPattern() {
        Optional<String> constraint = ConstraintNameExtractor.extract(
                "Duplicate entry 'x@datapillar.ai' for key 'uq_user_email'");

        Assertions.assertTrue(constraint.isPresent());
        Assertions.assertEquals("uq_user_email", constraint.get());
    }

    @Test
    void extract_shouldResolveConstraintPattern() {
        Optional<String> constraint = ConstraintNameExtractor.extract(
                "violates foreign key constraint `UQ_USER_IDENTITY_USER_PROVIDER`");

        Assertions.assertTrue(constraint.isPresent());
        Assertions.assertEquals("uq_user_identity_user_provider", constraint.get());
    }

    @Test
    void extract_shouldResolveFromThrowableChain() {
        SQLException sqlException = new SQLException(
                "Duplicate entry 'tenant-a' for key 'UQ_TENANT_CODE'",
                "23000",
                1062);
        RuntimeException runtimeException = new RuntimeException("db write failed", sqlException);

        Optional<String> constraint = ConstraintNameExtractor.extract(runtimeException);

        Assertions.assertTrue(constraint.isPresent());
        Assertions.assertEquals("uq_tenant_code", constraint.get());
    }

    @Test
    void extract_shouldReturnEmptyWhenMessageBlank() {
        Assertions.assertTrue(ConstraintNameExtractor.extract("   ").isEmpty());
        Assertions.assertTrue(ConstraintNameExtractor.extract((String) null).isEmpty());
    }
}
