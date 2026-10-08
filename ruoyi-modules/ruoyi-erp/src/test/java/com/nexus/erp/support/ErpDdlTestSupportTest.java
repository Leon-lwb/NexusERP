package com.nexus.erp.support;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Strict regression samples for MySQL 5.7 installation dump syntax, without a database. */
class ErpDdlTestSupportTest {
    @Test void oldAndDumpColumnsHaveEquivalentSemantics() {
        for (String metadata : List.of("", " COLLATE utf8mb4_unicode_ci", " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")) {
            ErpDdlTestSupport.assertColumn("  `tenant_id` varchar(20)" + metadata + " NOT NULL DEFAULT '000000',",
                "tenant_id", "varchar(20) NOT NULL DEFAULT '000000'");
        }
        for (String width : List.of("int", "int(11)")) for (String value : List.of("0", "'0'")) {
            ErpDdlTestSupport.assertColumn("  `version` " + width + " NOT NULL DEFAULT " + value + ",",
                "version", "int NOT NULL DEFAULT 0");
        }
        for (String type : List.of("decimal(12,2)", "decimal(12, 2)")) {
            ErpDdlTestSupport.assertColumn("  `planned_hours` " + type + " NOT NULL,", "planned_hours", "decimal(12,2) NOT NULL");
        }
        for (String nullable : List.of("NULL DEFAULT NULL", "DEFAULT NULL")) {
            ErpDdlTestSupport.assertColumn("  `certified_version` int(11) " + nullable + ",", "certified_version", "int DEFAULT NULL");
        }
    }

    @Test void unsafeTypePrecisionDefaultAndNullabilityAreNotRepaired() {
        for (String definition : List.of("bigint(20) NOT NULL DEFAULT 0", "int NOT NULL DEFAULT '1'", "int NOT NULL DEFAULT 2",
            "int DEFAULT 0", "int DEFAULT NULL", "int NOT NULL DEFAULT 0 DEFAULT 2")) {
            assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumn("  `version` " + definition,
                "version", "int NOT NULL DEFAULT 0"));
        }
        for (String definition : List.of("decimal(18,3) NOT NULL", "decimal(14,4) NOT NULL", "decimal(18,4) DEFAULT NULL")) {
            assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumn("  `quantity` " + definition,
                "quantity", "decimal(18,4) NOT NULL"));
        }
        for (String type : List.of("datetime", "datetime(6)")) {
            assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumn("  `observed_at` " + type + " NOT NULL",
                "observed_at", "datetime(3) NOT NULL"));
        }
        for (String value : List.of("'000001'", "000000", "'0'", "NULL", "'000000 COLLATE utf8mb4_unicode_ci'")) {
            assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumn("  `tenant_id` varchar(20) NOT NULL DEFAULT " + value,
                "tenant_id", "varchar(20) NOT NULL DEFAULT '000000'"));
        }
        assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumn("  `certified_version` int DEFAULT 2",
            "certified_version", "int DEFAULT NULL"));
        assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumn("  `event_sequence` int DEFAULT NULL",
            "event_sequence", "int NOT NULL"));
        assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumn("  `version` int NOT NULL DEFAULT 0,\n  `version` int NOT NULL DEFAULT 0",
            "version", "int NOT NULL DEFAULT 0"));
    }

    @Test void implicitNullableDefaultsStillProduceNativeColumnMetadata() {
        var columns = ErpDdlTestSupport.columns("  `returned_quantity` decimal(18,4) DEFAULT '0.0000',\n"
            + "  `tenant_id` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '000000',\n"
            + "  `observed_at` datetime(3) NOT NULL,\n  `counter` int(11) DEFAULT '0' COMMENT 'NOT NULL is documentation',");
        assertEquals(List.of("returned_quantity", "tenant_id", "observed_at", "counter"), columns.stream().map(ErpDdlTestSupport.Column::name).toList());
        assertEquals(new ErpDdlTestSupport.Column("returned_quantity", "decimal", 18, 4, null, true), columns.get(0));
        assertEquals(new ErpDdlTestSupport.Column("tenant_id", "varchar", null, null, 20L, true), columns.get(1));
        assertFalse(columns.get(2).nullable());
        assertTrue(columns.get(3).nullable());
    }

    @Test void indexOrderUniquenessPrefixesAndDuplicatesStayStrict() {
        for (String kind : List.of("KEY", "INDEX")) for (String btree : List.of("", " USING BTREE")) {
            String index = "  UNIQUE " + kind + " `uk` (`tenant_id`,`request_key`)" + btree + ",";
            ErpDdlTestSupport.assertIndex(index, true, "uk", "tenant_id", "request_key");
            ErpDdlTestSupport.assertUniqueIndexColumns(index, "tenant_id", "request_key");
            for (String changed : List.of(index.replace("UNIQUE ", ""), index.replace("`tenant_id`,`request_key`", "`request_key`,`tenant_id`"),
                index.replace("`request_key`", "`request_key`(12)"), index + "\n" + index)) {
                assertNotEquals(index, changed);
                assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertIndex(changed, true, "uk", "tenant_id", "request_key"));
                assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertUniqueIndexColumns(changed, "tenant_id", "request_key"));
            }
            String primary = "  PRIMARY KEY (`tenant_id`,`material_id`)" + btree + ",";
            ErpDdlTestSupport.assertPrimaryKey(primary, "tenant_id", "material_id");
            assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertPrimaryKey(primary, "material_id", "tenant_id"));
        }
    }

    @Test void explicitAndInheritedCollationsCannotMaskWrongTenantCollation() {
        for (String charset : List.of("", " CHARACTER SET utf8mb4")) {
            String table = "  `tenant_id` varchar(20)" + charset + " COLLATE utf8mb4_bin NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            ErpDdlTestSupport.assertColumnCollation(table, "tenant_id", "utf8mb4_bin");
            String changed = table.replace("COLLATE utf8mb4_bin", "COLLATE utf8mb4_unicode_ci");
            assertThrows(AssertionError.class, () -> ErpDdlTestSupport.assertColumnCollation(changed, "tenant_id", "utf8mb4_bin"));
        }
        ErpDdlTestSupport.assertColumnCollation("  `tenant_id` varchar(20) NOT NULL\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;",
            "tenant_id", "utf8mb4_bin");
    }
}
