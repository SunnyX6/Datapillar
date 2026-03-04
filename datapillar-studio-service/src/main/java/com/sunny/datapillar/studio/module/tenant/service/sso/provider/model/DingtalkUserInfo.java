package com.sunny.datapillar.studio.module.tenant.service.sso.provider.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DingtalkUserInfocomponents responsibleDingtalkUserInfoCore logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@AllArgsConstructor
public class DingtalkUserInfo {

  private String unionId;
  private String rawJson;
}
