package com.sunny.datapillar.studio.module.features.util;

import com.sunny.datapillar.studio.module.features.entity.Permission;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * 权限等级工具
 */
public final class PermissionLevelUtil {

    private PermissionLevelUtil() {
    }

    public static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() || "NONE".equals(normalized) ? null : normalized;
    }

    public static int level(Map<String, Permission> permissionMap, String code) {
        String normalized = normalizeCode(code);
        if (normalized == null) {
            return 0;
        }
        if (permissionMap == null) {
            return 0;
        }
        Permission permission = permissionMap.get(normalized);
        if (permission == null || permission.getLevel() == null) {
            return 0;
        }
        return permission.getLevel();
    }

    public static String maxCode(Map<String, Permission> permissionMap, Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "NONE";
        }
        String maxCode = null;
        int maxLevel = 0;
        for (String code : codes) {
            int level = level(permissionMap, code);
            if (level > maxLevel) {
                maxLevel = level;
                maxCode = normalizeCode(code);
            }
        }
        return maxCode == null ? "NONE" : maxCode;
    }

    public static String maxCode(Map<String, Permission> permissionMap, String codeA, String codeB) {
        int levelA = level(permissionMap, codeA);
        int levelB = level(permissionMap, codeB);
        if (levelA >= levelB) {
            return normalizeCode(codeA) == null ? "NONE" : normalizeCode(codeA);
        }
        return normalizeCode(codeB) == null ? "NONE" : normalizeCode(codeB);
    }

    public static String minCode(Map<String, Permission> permissionMap, String codeA, String codeB) {
        int levelA = level(permissionMap, codeA);
        int levelB = level(permissionMap, codeB);
        if (levelA == 0 || levelB == 0) {
            return "NONE";
        }
        if (levelA <= levelB) {
            return normalizeCode(codeA) == null ? "NONE" : normalizeCode(codeA);
        }
        return normalizeCode(codeB) == null ? "NONE" : normalizeCode(codeB);
    }
}
