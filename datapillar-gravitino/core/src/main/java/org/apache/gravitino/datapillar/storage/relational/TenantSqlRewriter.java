/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.datapillar.storage.relational;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to rewrite relational SQL with mandatory tenant predicates. */
public final class TenantSqlRewriter {

  private static final String TENANT_COLUMN = "tenant_id";
  private static final Set<String> SQL_KEYWORDS_AS_ALIAS =
      Set.of(
          "where",
          "join",
          "left",
          "right",
          "inner",
          "full",
          "cross",
          "on",
          "group",
          "having",
          "order",
          "limit",
          "union",
          "returning",
          "values",
          "set");

  private static final Pattern FROM_JOIN_TABLE_PATTERN =
      Pattern.compile(
          "(?i)\\b(from|join)\\s+([`\"A-Za-z0-9_.]+)(?:\\s+(?:as\\s+)?([`\"A-Za-z0-9_]+))?");
  private static final Pattern UPDATE_TABLE_PATTERN =
      Pattern.compile(
          "(?i)^\\s*update\\s+([`\"A-Za-z0-9_.]+)(?:\\s+(?:as\\s+)?([`\"A-Za-z0-9_]+))?");
  private static final Pattern DELETE_TABLE_PATTERN =
      Pattern.compile(
          "(?i)^\\s*delete\\s+from\\s+([`\"A-Za-z0-9_.]+)(?:\\s+(?:as\\s+)?([`\"A-Za-z0-9_]+))?");
  private static final Pattern DELETED_AT_ZERO_PATTERN =
      Pattern.compile("(?i)([A-Za-z0-9_`\"]+\\.)?deleted_at\\s*=\\s*0");

  private TenantSqlRewriter() {}

  public static String rewrite(
      String statementId, String sql, long tenantId, TenantSqlPatchRegistry patchRegistry) {
    if (sql == null || sql.isBlank()) {
      return sql;
    }

    String normalizedSql = normalize(sql);
    boolean complex = isComplexSql(normalizedSql);
    if (complex && !patchRegistry.registered(statementId)) {
      throw new IllegalStateException(
          String.format(
              "Complex SQL is not registered in TenantSqlPatchRegistry, statementId=%s",
              statementId));
    }

    if (normalizedSql.startsWith("insert")) {
      return rewriteInsertSql(sql, tenantId);
    }

    if (!normalizedSql.startsWith("select")
        && !normalizedSql.startsWith("update")
        && !normalizedSql.startsWith("delete")) {
      return sql;
    }

    if (complex) {
      return rewriteComplexSql(sql, tenantId);
    }

    return rewriteDmlSql(sql, tenantId);
  }

  private static String rewriteComplexSql(String sql, long tenantId) {
    String patchedByDeletedHint = rewriteDeletedAtPredicates(sql, tenantId);
    if (!patchedByDeletedHint.equals(sql)) {
      return patchedByDeletedHint;
    }
    return rewriteDmlSql(sql, tenantId);
  }

  private static String rewriteDeletedAtPredicates(String sql, long tenantId) {
    Matcher matcher = DELETED_AT_ZERO_PATTERN.matcher(sql);
    StringBuffer result = new StringBuffer(sql.length() + 64);
    boolean replaced = false;
    while (matcher.find()) {
      String qualifier = matcher.group(1) == null ? "" : matcher.group(1);
      String tenantPredicate = qualifier + TENANT_COLUMN + " = " + tenantId;
      String replacement = matcher.group(0) + " AND " + tenantPredicate;
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
      replaced = true;
    }
    if (!replaced) {
      return sql;
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static String rewriteInsertSql(String sql, long tenantId) {
    int intoIndex = findTopLevelKeyword(sql, "INTO", 0);
    if (intoIndex < 0) {
      return sql;
    }

    int columnsStart = sql.indexOf('(', intoIndex);
    if (columnsStart < 0) {
      return sql;
    }

    int columnsEnd = findMatchingParentheses(sql, columnsStart);
    if (columnsEnd < 0) {
      return sql;
    }

    String columns = sql.substring(columnsStart + 1, columnsEnd);
    if (containsColumn(columns, TENANT_COLUMN)) {
      return sql;
    }

    int valuesKeywordIndex = findTopLevelKeyword(sql, "VALUES", columnsEnd);
    if (valuesKeywordIndex < 0) {
      throw new IllegalStateException("Unsupported INSERT SQL for tenant rewrite: " + sql);
    }

    int valuesContentStart = valuesKeywordIndex + "VALUES".length();
    int valuesContentEnd =
        findFirstTopLevelKeyword(
            sql, valuesContentStart, "ON DUPLICATE KEY", "ON CONFLICT", "RETURNING");
    if (valuesContentEnd < 0) {
      valuesContentEnd = sql.length();
    }

    String valueTuples = sql.substring(valuesContentStart, valuesContentEnd);
    String rewrittenValueTuples = appendTenantLiteralToValues(valueTuples, tenantId);

    StringBuilder rewritten = new StringBuilder(sql.length() + 64);
    rewritten
        .append(sql, 0, columnsEnd)
        .append(", ")
        .append(TENANT_COLUMN)
        .append(sql, columnsEnd, valuesContentStart)
        .append(rewrittenValueTuples)
        .append(sql.substring(valuesContentEnd));
    return rewritten.toString();
  }

  private static String appendTenantLiteralToValues(String valuesPart, long tenantId) {
    StringBuilder rewritten = new StringBuilder(valuesPart.length() + 32);
    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inBacktick = false;
    boolean tupleStarted = false;

    for (int index = 0; index < valuesPart.length(); index++) {
      char current = valuesPart.charAt(index);
      char previous = index == 0 ? '\0' : valuesPart.charAt(index - 1);

      if (current == '\'' && !inDoubleQuote && !inBacktick && previous != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (current == '"' && !inSingleQuote && !inBacktick && previous != '\\') {
        inDoubleQuote = !inDoubleQuote;
      } else if (current == '`' && !inSingleQuote && !inDoubleQuote) {
        inBacktick = !inBacktick;
      }

      if (!inSingleQuote && !inDoubleQuote && !inBacktick) {
        if (current == '(') {
          depth++;
          tupleStarted = true;
        } else if (current == ')') {
          if (depth == 1 && tupleStarted) {
            rewritten.append(", ").append(tenantId);
          }
          depth = Math.max(0, depth - 1);
        }
      }

      rewritten.append(current);
    }

    if (!tupleStarted) {
      throw new IllegalStateException("Unsupported VALUES SQL for tenant rewrite: " + valuesPart);
    }

    return rewritten.toString();
  }

  private static String rewriteDmlSql(String sql, long tenantId) {
    List<String> qualifiers = extractQualifiers(sql);
    String tenantPredicate = buildTenantPredicate(qualifiers, tenantId);
    return appendPredicate(sql, tenantPredicate);
  }

  private static List<String> extractQualifiers(String sql) {
    Set<String> qualifiers = new LinkedHashSet<>();

    Matcher updateMatcher = UPDATE_TABLE_PATTERN.matcher(sql);
    if (updateMatcher.find()) {
      qualifiers.add(resolveQualifier(updateMatcher.group(1), updateMatcher.group(2)));
    }

    Matcher deleteMatcher = DELETE_TABLE_PATTERN.matcher(sql);
    if (deleteMatcher.find()) {
      qualifiers.add(resolveQualifier(deleteMatcher.group(1), deleteMatcher.group(2)));
    }

    Matcher fromJoinMatcher = FROM_JOIN_TABLE_PATTERN.matcher(sql);
    while (fromJoinMatcher.find()) {
      String table = fromJoinMatcher.group(2);
      if (table == null || table.startsWith("(")) {
        continue;
      }
      qualifiers.add(resolveQualifier(table, fromJoinMatcher.group(3)));
    }

    List<String> results = new ArrayList<>();
    for (String qualifier : qualifiers) {
      if (qualifier != null && !qualifier.isBlank()) {
        results.add(qualifier);
      }
    }
    return results;
  }

  private static String buildTenantPredicate(List<String> qualifiers, long tenantId) {
    if (qualifiers.isEmpty()) {
      return TENANT_COLUMN + " = " + tenantId;
    }

    List<String> predicates = new ArrayList<>();
    for (String qualifier : qualifiers) {
      predicates.add(qualifier + "." + TENANT_COLUMN + " = " + tenantId);
    }

    String base = qualifiers.get(0) + "." + TENANT_COLUMN;
    for (int index = 1; index < qualifiers.size(); index++) {
      predicates.add(base + " = " + qualifiers.get(index) + "." + TENANT_COLUMN);
    }

    return String.join(" AND ", predicates);
  }

  private static String appendPredicate(String sql, String predicate) {
    int whereIndex = findTopLevelKeyword(sql, "WHERE", 0);
    if (whereIndex >= 0) {
      int endIndex =
          findFirstTopLevelKeyword(
              sql,
              whereIndex + "WHERE".length(),
              "GROUP BY",
              "HAVING",
              "ORDER BY",
              "LIMIT",
              "RETURNING");
      if (endIndex < 0) {
        endIndex = sql.length();
      }
      return sql.substring(0, endIndex) + " AND (" + predicate + ")" + sql.substring(endIndex);
    }

    int insertIndex =
        findFirstTopLevelKeyword(sql, 0, "GROUP BY", "HAVING", "ORDER BY", "LIMIT", "RETURNING");
    if (insertIndex < 0) {
      insertIndex = sql.length();
    }
    return sql.substring(0, insertIndex)
        + " WHERE ("
        + predicate
        + ")"
        + sql.substring(insertIndex);
  }

  private static int findFirstTopLevelKeyword(String sql, int fromIndex, String... keywords) {
    int minIndex = -1;
    for (String keyword : keywords) {
      int keywordIndex = findTopLevelKeyword(sql, keyword, fromIndex);
      if (keywordIndex >= 0 && (minIndex < 0 || keywordIndex < minIndex)) {
        minIndex = keywordIndex;
      }
    }
    return minIndex;
  }

  private static int findTopLevelKeyword(String sql, String keyword, int fromIndex) {
    String upperSql = sql.toUpperCase(Locale.ROOT);
    String upperKeyword = keyword.toUpperCase(Locale.ROOT);

    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inBacktick = false;

    for (int index = 0; index < sql.length(); index++) {
      char current = sql.charAt(index);
      char previous = index == 0 ? '\0' : sql.charAt(index - 1);

      if (current == '\'' && !inDoubleQuote && !inBacktick && previous != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (current == '"' && !inSingleQuote && !inBacktick && previous != '\\') {
        inDoubleQuote = !inDoubleQuote;
      } else if (current == '`' && !inSingleQuote && !inDoubleQuote) {
        inBacktick = !inBacktick;
      }

      if (inSingleQuote || inDoubleQuote || inBacktick) {
        continue;
      }

      if (current == '(') {
        depth++;
        continue;
      }
      if (current == ')') {
        depth = Math.max(0, depth - 1);
        continue;
      }

      if (depth != 0 || index < fromIndex) {
        continue;
      }

      if (!upperSql.startsWith(upperKeyword, index)) {
        continue;
      }

      if (!isKeywordBoundary(upperSql, index - 1)
          || !isKeywordBoundary(upperSql, index + upperKeyword.length())) {
        continue;
      }

      return index;
    }

    return -1;
  }

  private static boolean isKeywordBoundary(String sql, int index) {
    if (index < 0 || index >= sql.length()) {
      return true;
    }

    char current = sql.charAt(index);
    return !Character.isLetterOrDigit(current) && current != '_' && current != '.';
  }

  private static int findMatchingParentheses(String sql, int startIndex) {
    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inBacktick = false;

    for (int index = startIndex; index < sql.length(); index++) {
      char current = sql.charAt(index);
      char previous = index == 0 ? '\0' : sql.charAt(index - 1);

      if (current == '\'' && !inDoubleQuote && !inBacktick && previous != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (current == '"' && !inSingleQuote && !inBacktick && previous != '\\') {
        inDoubleQuote = !inDoubleQuote;
      } else if (current == '`' && !inSingleQuote && !inDoubleQuote) {
        inBacktick = !inBacktick;
      }

      if (inSingleQuote || inDoubleQuote || inBacktick) {
        continue;
      }

      if (current == '(') {
        depth++;
      } else if (current == ')') {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
    }

    return -1;
  }

  private static boolean containsColumn(String columns, String expectedColumn) {
    String[] splitColumns = columns.split(",");
    for (String rawColumn : splitColumns) {
      String normalizedColumn = stripIdentifier(rawColumn.trim());
      if (expectedColumn.equalsIgnoreCase(normalizedColumn)) {
        return true;
      }
    }
    return false;
  }

  private static String resolveQualifier(String rawTable, String rawAlias) {
    if (rawAlias != null && !rawAlias.isBlank()) {
      String alias = stripIdentifier(rawAlias);
      if (!SQL_KEYWORDS_AS_ALIAS.contains(alias.toLowerCase(Locale.ROOT))) {
        return alias;
      }
    }

    String table = stripIdentifier(rawTable);
    int dotIndex = table.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex < table.length() - 1) {
      return table.substring(dotIndex + 1);
    }
    return table;
  }

  private static String stripIdentifier(String identifier) {
    if (identifier == null) {
      return null;
    }

    String value = identifier.trim();
    if ((value.startsWith("`") && value.endsWith("`"))
        || (value.startsWith("\"") && value.endsWith("\""))) {
      value = value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static boolean isComplexSql(String normalizedSql) {
    return normalizedSql.contains(" union ")
        || normalizedSql.startsWith("with ")
        || normalizedSql.contains(" join (")
        || normalizedSql.contains(" exists (select")
        || normalizedSql.contains(" in (select")
        || normalizedSql.contains(" from (select")
        || (normalizedSql.startsWith("insert") && normalizedSql.contains(" select "));
  }

  private static String normalize(String sql) {
    return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
  }
}
