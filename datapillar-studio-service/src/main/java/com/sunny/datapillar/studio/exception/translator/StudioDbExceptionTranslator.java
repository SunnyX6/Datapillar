package com.sunny.datapillar.studio.exception.translator;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.db.DbCheckConstraintViolationException;
import com.sunny.datapillar.common.exception.db.DbConnectionFailedException;
import com.sunny.datapillar.common.exception.db.DbDataTooLongException;
import com.sunny.datapillar.common.exception.db.DbDeadlockException;
import com.sunny.datapillar.common.exception.db.DbForeignKeyViolationException;
import com.sunny.datapillar.common.exception.db.DbLockTimeoutException;
import com.sunny.datapillar.common.exception.db.DbNotNullViolationException;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.DbUniqueConstraintViolationException;
import com.sunny.datapillar.studio.exception.llm.AiModelAlreadyExistsException;
import com.sunny.datapillar.studio.exception.llm.AiModelGrantConflictException;
import com.sunny.datapillar.studio.exception.llm.AiProviderAlreadyExistsException;
import com.sunny.datapillar.studio.exception.user.UserEmailAlreadyExistsException;
import com.sunny.datapillar.studio.exception.user.UsernameAlreadyExistsException;
import com.sunny.datapillar.studio.exception.sso.SsoConfigAlreadyExistsException;
import com.sunny.datapillar.studio.exception.sso.SsoIdentityAlreadyExistsException;
import com.sunny.datapillar.studio.exception.tenant.TenantCodeAlreadyExistsException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Studio DB 异常业务转换器
 *
 * @author Sunny
 * @date 2026-02-26
 */
@Component
public class StudioDbExceptionTranslator {

    public DatapillarRuntimeException map(StudioDbScene scene, DbStorageException dbException) {
        if (dbException instanceof DbUniqueConstraintViolationException uniqueConstraintViolationException) {
            return mapUnique(scene, uniqueConstraintViolationException);
        }

        if (dbException instanceof DbForeignKeyViolationException
                || dbException instanceof DbNotNullViolationException
                || dbException instanceof DbCheckConstraintViolationException
                || dbException instanceof DbDataTooLongException) {
            return new BadRequestException(dbException, "参数错误");
        }

        if (dbException instanceof DbDeadlockException
                || dbException instanceof DbLockTimeoutException
                || dbException instanceof DbConnectionFailedException) {
            return new ServiceUnavailableException(dbException, "服务不可用");
        }

        return new InternalException(dbException, ErrorType.STUDIO_DB_INTERNAL, Map.of(), "服务器内部错误");
    }

    private DatapillarRuntimeException mapUnique(StudioDbScene scene,
                                                 DbUniqueConstraintViolationException dbException) {
        String constraintName = dbException.getConstraintName();
        if (scene == StudioDbScene.STUDIO_INVITATION_REGISTER) {
            if ("uq_user_email".equals(constraintName)) {
                return new UserEmailAlreadyExistsException(dbException);
            }
            if ("uq_user_username".equals(constraintName)) {
                return new UsernameAlreadyExistsException(dbException);
            }
        }

        if (scene == StudioDbScene.STUDIO_SSO_IDENTITY_BIND) {
            if ("uq_user_identity".equals(constraintName)
                    || "uq_user_identity_user_provider".equals(constraintName)) {
                return new SsoIdentityAlreadyExistsException(dbException);
            }
        }

        if (scene == StudioDbScene.STUDIO_SSO_CONFIG
                && "uq_tenant_sso".equals(constraintName)) {
            return new SsoConfigAlreadyExistsException(dbException);
        }

        if (scene == StudioDbScene.STUDIO_TENANT_MANAGE
                && "uq_tenant_code".equals(constraintName)) {
            return new TenantCodeAlreadyExistsException(dbException);
        }

        if (scene == StudioDbScene.STUDIO_LLM_PROVIDER_CREATE
                && "uq_ai_provider_code".equals(constraintName)) {
            return new AiProviderAlreadyExistsException(dbException);
        }

        if (scene == StudioDbScene.STUDIO_LLM_MODEL_CREATE
                && "uq_ai_model_tenant_provider_model".equals(constraintName)) {
            return new AiModelAlreadyExistsException(dbException);
        }

        if (scene == StudioDbScene.STUDIO_LLM_MODEL_GRANT
                && ("uq_ai_model_grant_tenant_user_model".equals(constraintName)
                || "uq_ai_model_grant_tenant_user_default".equals(constraintName))) {
            return new AiModelGrantConflictException(dbException);
        }

        return new AlreadyExistsException(dbException, ErrorType.STUDIO_DB_DUPLICATE, Map.of(), "数据已存在");
    }
}
