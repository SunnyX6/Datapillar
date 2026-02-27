package com.sunny.datapillar.common.exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArchitectureStaticValidationTest {

    private static final Pattern RAW_ILLEGAL_ARGUMENT = Pattern.compile("throw\\s+new\\s+IllegalArgumentException\\(");

    private static final Pattern LEGACY_JDBC_EXCEPTION_CHAIN = Pattern.compile(
            "JdbcExceptionConverter|JdbcExceptionScene|JdbcExceptionRuleRegistry|toDatapillarException\\(");

    private static final Pattern LEGACY_SYSTEM_EXCEPTION = Pattern.compile(
            "common\\.exception\\.system|SystemBadRequestException|SystemInternalException|SystemUnauthorizedException");

    @Test
    void shouldNotThrowRawIllegalArgumentExceptionInServiceModules() throws IOException {
        List<String> violations = findViolations(RAW_ILLEGAL_ARGUMENT, serviceSourceRoots());
        Assertions.assertTrue(violations.isEmpty(), "发现裸 IllegalArgumentException:\n" + String.join("\n", violations));
    }

    @Test
    void shouldNotUseLegacyJdbcExceptionInfrastructure() throws IOException {
        List<String> violations = findViolations(LEGACY_JDBC_EXCEPTION_CHAIN, serviceSourceRoots());
        Assertions.assertTrue(violations.isEmpty(), "发现旧 JDBC 异常链路残留:\n" + String.join("\n", violations));
    }

    @Test
    void shouldNotUseLegacySystemExceptionPackage() throws IOException {
        List<String> violations = findViolations(LEGACY_SYSTEM_EXCEPTION, allSourceRoots());
        Assertions.assertTrue(violations.isEmpty(), "发现已废弃 system 异常残留:\n" + String.join("\n", violations));
    }

    private List<Path> serviceSourceRoots() {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        return List.of(
                projectRoot.resolve("datapillar-auth/src/main/java"),
                projectRoot.resolve("datapillar-studio-service/src/main/java"),
                projectRoot.resolve("datapillar-api-gateway/src/main/java")
        );
    }

    private List<Path> allSourceRoots() {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        return List.of(
                projectRoot.resolve("datapillar-common/src/main/java"),
                projectRoot.resolve("datapillar-auth/src/main/java"),
                projectRoot.resolve("datapillar-studio-service/src/main/java"),
                projectRoot.resolve("datapillar-api-gateway/src/main/java")
        );
    }

    private List<String> findViolations(Pattern pattern, List<Path> roots) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .forEach(path -> checkFile(pattern, root, path, violations));
            }
        }
        return violations;
    }

    private void checkFile(Pattern pattern, Path root, Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    Path relative = root.relativize(file);
                    violations.add(relative + ":" + (i + 1) + " => " + lines.get(i).trim());
                }
            }
        } catch (IOException ignored) {
            violations.add(file + ":读取失败");
        }
    }
}
