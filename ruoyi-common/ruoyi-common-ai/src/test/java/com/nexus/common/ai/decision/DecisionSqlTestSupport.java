package com.nexus.common.ai.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounded test-only translation of installation column DDL, never an SQL execution/safety parser. */
final class DecisionSqlTestSupport {

    static final String SQL_LITERAL = "'(?:''|\\\\.|[^'\\\\])*'";
    private static final Pattern TOKEN = Pattern.compile("\\s*(" + SQL_LITERAL + "|[^\\s']+)");

    private DecisionSqlTestSupport() {
    }

    static String columnDefinition(String table, String name) {
        Matcher column = Pattern.compile("(?m)^\\s*`" + Pattern.quote(name) + "`\\s+([^\\r\\n]+)$")
            .matcher(table);
        assertTrue(column.find(), "Missing column: " + name);
        String definition = normalizeColumnDefinition(column.group(1));
        assertFalse(column.find(), "Duplicate column: " + name);
        return definition;
    }

    static String h2Column(String line) {
        Matcher column = Pattern.compile("^(`[^`]+`)\\s+(.+)$").matcher(line.strip());
        assertTrue(column.matches(), "Expected one installation column definition");
        return column.group(1) + " " + normalizeColumnDefinition(column.group(2));
    }

    static String normalizeColumnDefinition(String definition) {
        String source = definition.strip().replaceFirst(",\\s*$", "").stripTrailing();
        List<String> tokens = new ArrayList<>();
        Matcher token = TOKEN.matcher(source);
        int offset = 0;
        while (offset < source.length()) {
            token.region(offset, source.length());
            assertTrue(token.lookingAt(), "Malformed column definition at offset " + offset);
            tokens.add(token.group(1));
            offset = token.end();
        }
        assertFalse(tokens.isEmpty(), "Empty column definition");
        int size = tokens.size();
        if (size >= 3 && tokens.get(size - 2).equalsIgnoreCase("COMMENT")
            && tokens.get(size - 1).matches(SQL_LITERAL)) {
            tokens.subList(size - 2, size).clear();
        }
        String type = tokens.get(0).toLowerCase(Locale.ROOT)
            .replaceFirst("^(tinyint|smallint|mediumint|int|bigint)\\(\\d+\\)$", "$1");
        tokens.set(0, type);
        if (type.matches("(?:var)?char\\(\\d+\\)|(?:tiny|medium|long)?text")) {
            // Only remove leading type metadata, never text inside a quoted DEFAULT/COMMENT.
            if (tokens.size() >= 4 && tokens.get(1).equalsIgnoreCase("CHARACTER")
                && tokens.get(2).equalsIgnoreCase("SET") && tokens.get(3).equalsIgnoreCase("utf8mb4")) {
                tokens.subList(1, 4).clear();
            }
            if (tokens.size() >= 3 && tokens.get(1).equalsIgnoreCase("COLLATE")
                && tokens.get(2).matches("(?i)utf8mb4_[a-z0-9_]+")) {
                tokens.subList(1, 3).clear();
            }
        }
        for (int i = 1; i < tokens.size(); i++) {
            if (!tokens.get(i).startsWith("'")) {
                tokens.set(i, tokens.get(i).toUpperCase(Locale.ROOT));
            }
        }
        if (type.matches("tinyint|smallint|mediumint|int|bigint")) {
            for (int i = 1; i + 1 < tokens.size(); i++) {
                if (tokens.get(i).equals("DEFAULT") && tokens.get(i + 1).matches("'-?\\d+'")) {
                    String value = tokens.get(i + 1);
                    tokens.set(i + 1, value.substring(1, value.length() - 1));
                }
            }
        }
        // MySQL's omitted NULL and explicit NULL before DEFAULT NULL are equivalent.
        if (tokens.size() >= 4 && tokens.subList(1, 4).equals(List.of("NULL", "DEFAULT", "NULL"))) {
            tokens.remove(1);
        }
        return String.join(" ", tokens);
    }
}
