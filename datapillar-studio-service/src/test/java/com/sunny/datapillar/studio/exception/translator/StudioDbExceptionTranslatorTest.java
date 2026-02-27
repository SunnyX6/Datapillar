package com.sunny.datapillar.studio.exception.translator;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.db.DbDeadlockException;
import com.sunny.datapillar.common.exception.db.DbUniqueConstraintViolationException;
import com.sunny.datapillar.studio.exception.llm.AiProviderAlreadyExistsException;
import com.sunny.datapillar.studio.exception.user.UserEmailAlreadyExistsException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StudioDbExceptionTranslatorTest {

    private final StudioDbExceptionTranslator translator = new StudioDbExceptionTranslator();

    @Test
    void map_shouldResolveInvitationEmailDuplicateByConstraint() {
        DbUniqueConstraintViolationException dbException = new DbUniqueConstraintViolationException(
                new RuntimeException("duplicate"),
                1062,
                "23000",
                "uq_user_email"
        );

        RuntimeException runtimeException = translator.map(StudioDbScene.STUDIO_INVITATION_REGISTER, dbException);

        Assertions.assertInstanceOf(UserEmailAlreadyExistsException.class, runtimeException);
    }

    @Test
    void map_shouldResolveLlmProviderDuplicateByConstraint() {
        DbUniqueConstraintViolationException dbException = new DbUniqueConstraintViolationException(
                new RuntimeException("duplicate"),
                1062,
                "23000",
                "uq_ai_provider_code"
        );

        RuntimeException runtimeException = translator.map(StudioDbScene.STUDIO_LLM_PROVIDER_CREATE, dbException);

        Assertions.assertInstanceOf(AiProviderAlreadyExistsException.class, runtimeException);
    }

    @Test
    void map_shouldFallbackToGenericAlreadyExistsWhenConstraintUnknown() {
        DbUniqueConstraintViolationException dbException = new DbUniqueConstraintViolationException(
                new RuntimeException("duplicate"),
                1062,
                "23000",
                "uq_unknown"
        );

        RuntimeException runtimeException = translator.map(StudioDbScene.STUDIO_GENERIC, dbException);

        Assertions.assertInstanceOf(AlreadyExistsException.class, runtimeException);
        Assertions.assertEquals("数据已存在", runtimeException.getMessage());
    }

    @Test
    void map_shouldMapDeadlockToServiceUnavailable() {
        DbDeadlockException dbException = new DbDeadlockException(new RuntimeException("deadlock"), 1213, "40001", null);

        RuntimeException runtimeException = translator.map(StudioDbScene.STUDIO_GENERIC, dbException);

        Assertions.assertEquals("服务不可用", runtimeException.getMessage());
    }
}
