package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoClientFactory;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoSystemConstants;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GravitinoDomainRoutingService {

  public static final String DOMAIN_ALL = "ALL";
  public static final String DOMAIN_METADATA = "METADATA";
  public static final String DOMAIN_SEMANTIC = "SEMANTIC";

  private static final Set<String> SEMANTIC_OBJECT_TYPES =
      Set.of("METRIC", "WORDROOT", "UNIT", "MODIFIER", "VALUE_DOMAIN");

  private final GravitinoClientFactory clientFactory;

  public String managedMetalake() {
    return clientFactory.requiredMetalake();
  }

  public String resolveMetalake(String domain) {
    return switch (normalizeDomain(domain)) {
      case DOMAIN_METADATA, DOMAIN_SEMANTIC -> managedMetalake();
      default -> throw new BadRequestException("Unsupported domain: %s", domain);
    };
  }

  public String resolveDomainByObject(String objectType, String objectName) {
    return isSemanticObject(objectType, objectName) ? DOMAIN_SEMANTIC : DOMAIN_METADATA;
  }

  public boolean matchesDomain(String domain, String objectType, String objectName) {
    String normalizedDomain = normalizeDomain(domain);
    if (DOMAIN_ALL.equals(normalizedDomain)) {
      return true;
    }
    return normalizedDomain.equals(resolveDomainByObject(objectType, objectName));
  }

  public String normalizeDomain(String domain) {
    if (!StringUtils.hasText(domain)) {
      return DOMAIN_ALL;
    }
    String normalizedDomain = domain.trim().toUpperCase(Locale.ROOT);
    if (DOMAIN_METADATA.equals(normalizedDomain)
        || DOMAIN_SEMANTIC.equals(normalizedDomain)
        || DOMAIN_ALL.equals(normalizedDomain)) {
      return normalizedDomain;
    }
    throw new BadRequestException("Unsupported domain: %s", domain);
  }

  private boolean isSemanticObject(String objectType, String objectName) {
    String normalizedObjectType = normalizeObjectType(objectType);
    if (SEMANTIC_OBJECT_TYPES.contains(normalizedObjectType)) {
      return true;
    }
    if (!StringUtils.hasText(objectName)) {
      return false;
    }
    String normalizedObjectName = objectName.trim();
    return normalizedObjectName.equals(GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS)
        || normalizedObjectName.startsWith(GravitinoSystemConstants.SEMANTIC_CATALOG_ONE_DS + ".");
  }

  private String normalizeObjectType(String objectType) {
    if (!StringUtils.hasText(objectType)) {
      throw new BadRequestException("objectType must not be blank");
    }
    return objectType.trim().toUpperCase(Locale.ROOT);
  }
}
