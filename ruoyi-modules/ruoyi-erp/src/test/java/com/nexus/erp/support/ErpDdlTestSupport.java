package com.nexus.erp.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounded test-only translation of installation column DDL, never an SQL execution/safety parser. */
public final class ErpDdlTestSupport {

    static final String SQL_LITERAL = "'(?:''|\\\\.|[^'\\\\])*'";
    private static final Pattern TOKEN = Pattern.compile("\\s*(" + SQL_LITERAL + "|[^\\s']+)");

    private ErpDdlTestSupport() {
    }

    public static String columnDefinition(String table, String name) {
        Matcher column = Pattern.compile("(?m)^\\s*`" + Pattern.quote(name) + "`\\s+([^\\r\\n]+)$")
            .matcher(table);
        assertTrue(column.find(), "Missing column: " + name);
        String definition = normalizeColumnDefinition(column.group(1));
        assertFalse(column.find(), "Duplicate column: " + name);
        return definition;
    }

    public static String normalizeColumnDefinition(String definition) {
        String source = definition.strip().replaceFirst(",\\s*$", "").stripTrailing()
            .replaceFirst("(?i)^([a-z]+)\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", "$1($2,$3)");
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
                && tokens.get(2).equalsIgnoreCase("SET") && tokens.get(3).matches("(?i)utf8mb4|ascii")) {
                tokens.subList(1, 4).clear();
            }
            if (tokens.size() >= 3 && tokens.get(1).equalsIgnoreCase("COLLATE")
                && tokens.get(2).matches("(?i)(?:utf8mb4|ascii)_[a-z0-9_]+")) {
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

    public static void assertColumn(String table, String name, String expected) {
        assertEquals(expected, columnDefinition(table, name), "Column contract: " + name);
    }

    /** Only installation metadata for test mocks; production must still query information_schema. */
    public static List<Column> columns(String table) {
        List<Column> result = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String line : table.split("\\R")) {
            if (!line.stripLeading().startsWith("`")) continue;
            Matcher name = Pattern.compile("^\\s*`([^`]+)`\\s+(.+)$").matcher(line);
            assertTrue(name.matches(), "Malformed installation column");
            assertTrue(names.add(name.group(1)), "Duplicate installation column: " + name.group(1));
            String definition = normalizeColumnDefinition(name.group(2));
            Matcher type = Pattern.compile("^([a-z]+)(?:\\((\\d+)(?:,\\s*(\\d+))?\\))?(?:\\s|$)").matcher(definition);
            assertTrue(type.find(), "Unsupported installation column type: " + name.group(1));
            List<String> tokens = new ArrayList<>();
            Matcher token = TOKEN.matcher(definition);
            while (token.find()) tokens.add(token.group(1));
            boolean nullable = true;
            for (int i = 1; i + 1 < tokens.size(); i++) {
                if (tokens.get(i).equals("NOT") && tokens.get(i + 1).equals("NULL")) nullable = false;
            }
            String dataType = type.group(1);
            result.add(new Column(name.group(1), dataType,
                dataType.equals("decimal") ? Integer.valueOf(type.group(2)) : null,
                dataType.equals("decimal") ? Integer.valueOf(type.group(3)) : null,
                dataType.matches("varchar|char") ? Long.valueOf(type.group(2)) : null, nullable));
        }
        assertFalse(result.isEmpty(), "Installation columns must not be empty");
        return result;
    }

    public record Column(String name, String type, Integer precision, Integer scale, Long maxLength, boolean nullable) { }

    public static void assertColumnCollation(String table, String name, String expected) {
        Matcher column = Pattern.compile("(?m)^\\s*`" + Pattern.quote(name) + "`\\s+([^\\r\\n]+)$").matcher(table);
        assertTrue(column.find(), "Missing column: " + name);
        Matcher token = TOKEN.matcher(column.group(1));
        String collation = null;
        while (token.find()) {
            if (token.group(1).equalsIgnoreCase("COMMENT")) break;
            if (token.group(1).equalsIgnoreCase("COLLATE")) {
                assertEquals(null, collation, "Duplicate column COLLATE: " + name);
                assertTrue(token.find(), "Missing column collation: " + name);
                collation = token.group(1);
            }
        }
        if (collation == null) {
            Matcher inherited = Pattern.compile("(?i)\\)\\s*ENGINE[^;\\r\\n]*\\bCOLLATE\\s*=\\s*([a-z0-9_]+)").matcher(table);
            assertTrue(inherited.find(), "Missing effective column collation: " + name);
            collation = inherited.group(1);
        }
        assertEquals(expected.toLowerCase(Locale.ROOT), collation.toLowerCase(Locale.ROOT), "Column collation: " + name);
    }

    public static void assertIndex(String table, boolean unique, String name, String... columns) {
        Matcher index = Pattern.compile("(?im)^\\s*(UNIQUE\\s+)?(?:KEY|INDEX)\\s+`" + Pattern.quote(name)
            + "`\\s*\\(([^)]*)\\)(?:\\s+USING\\s+BTREE)?\\s*,?\\s*$").matcher(table);
        assertTrue(index.find(), "Missing index: " + name);
        assertEquals(unique, index.group(1) != null, "Index uniqueness: " + name);
        assertEquals(java.util.Arrays.stream(columns).map(c -> "`" + c + "`").toList(), indexColumns(index.group(2)),
            "Index columns/order: " + name);
        assertFalse(index.find(), "Duplicate index: " + name);
    }

    public static void assertPrimaryKey(String table, String... columns) {
        Matcher key = Pattern.compile("(?im)^\\s*PRIMARY\\s+KEY\\s*\\(([^)]*)\\)(?:\\s+USING\\s+BTREE)?\\s*,?\\s*$").matcher(table);
        assertTrue(key.find(), "Missing primary key");
        assertEquals(java.util.Arrays.stream(columns).map(c -> "`" + c + "`").toList(), indexColumns(key.group(1)),
            "Primary key columns/order");
        assertFalse(key.find(), "Duplicate primary key");
    }

    public static void assertUniqueIndexColumns(String table, String... columns) {
        List<String> expected = java.util.Arrays.stream(columns).map(c -> "`" + c + "`").toList();
        Matcher index = Pattern.compile("(?im)^\\s*UNIQUE\\s+(?:KEY|INDEX)\\s+`([^`]+)`\\s*\\(([^)]*)\\)(?:\\s+USING\\s+BTREE)?\\s*,?\\s*$").matcher(table);
        List<String> names = new ArrayList<>();
        while (index.find()) if (expected.equals(indexColumns(index.group(2)))) names.add(index.group(1));
        assertEquals(1, names.size(), "Exactly one unique index must cover " + String.join(",", columns));
        assertIndex(table, true, names.get(0), columns);
    }

    private static List<String> indexColumns(String list) {
        return java.util.Arrays.stream(list.split(",", -1)).map(String::strip).toList();
    }

}
