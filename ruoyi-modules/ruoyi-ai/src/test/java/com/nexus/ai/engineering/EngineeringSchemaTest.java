package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.*;

class EngineeringSchemaTest {
    @Test
    void schemaPreservesOwnerAndEnforcesIdempotencyAndCas() throws Exception {
        try (var in = getClass().getResourceAsStream("/engineering/schema-h2.sql")) {
            assertNotNull(in, "独立工程测试结构必须存在");
            try (var c =
                    DriverManager.getConnection(
                            "jdbc:h2:mem:engineering_schema;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
                for (String sql : new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";"))
                    if (!sql.isBlank()) c.createStatement().execute(sql);
                String insert =
                        "insert into"
                            + " ai_engineering_task(task_id,tenant_id,user_id,device_id,action,idempotency_key,request_hash,state,attempt,revision)"
                            + " values(1,'000001',2,3,'CHECK_CONNECTION','key','hash','WAITING_DEVICE',0,0)";
                c.createStatement().executeUpdate(insert);
                assertThrows(
                        SQLException.class,
                        () ->
                                c.createStatement()
                                        .executeUpdate(insert.replace("values(1,", "values(2,")));
                try (var rs =
                        c.createStatement()
                                .executeQuery(
                                        "select tenant_id from ai_engineering_task where"
                                            + " task_id=1")) {
                    assertTrue(rs.next());
                    assertEquals("000001", rs.getString(1));
                }
                String cas =
                        "update ai_engineering_task set state='RUNNING', revision=revision+1 where"
                            + " task_id=1 and tenant_id='000001' and user_id=2 and"
                            + " state='WAITING_DEVICE' and revision=0";
                assertEquals(1, c.createStatement().executeUpdate(cas));
                assertEquals(0, c.createStatement().executeUpdate(cas));
            }
        }
    }
}
