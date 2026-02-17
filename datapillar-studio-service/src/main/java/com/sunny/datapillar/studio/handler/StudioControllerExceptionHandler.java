package com.sunny.datapillar.studio.handler;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * StudioController异常处理器
 * 负责StudioController异常处理流程与结果输出
 *
 * @author Sunny
 * @date 2026-01-01
 */
@RestControllerAdvice
public class StudioControllerExceptionHandler extends BaseControllerExceptionHandler {

    @Override
    protected String resolveDuplicateKeyMessage(DuplicateKeyException exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("template_parameters.uk_template_param")) {
            return "该模板中已存在相同的参数键，请使用不同的参数键名称";
        }
        return "数据已存在，请检查输入内容";
    }
}
