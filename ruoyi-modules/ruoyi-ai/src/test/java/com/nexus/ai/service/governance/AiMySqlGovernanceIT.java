package com.nexus.ai.service.governance;

import static org.assertj.core.api.Assertions.*;

import com.nexus.common.ai.domain.entity.AiMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.*;
import java.time.*;
import java.util.regex.*;

/** 只由专用启动器在新建隔离库运行；缺少环境时失败而不是跳过。 */
class AiMySqlGovernanceIT extends AiCallLedgerServiceTest {
    @Override
    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("AI_GOV_TEST_URL"), nonce = System.getenv("AI_GOV_TEST_NONCE");
        assertThat(url)
                .matches(
                        "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):3306/nexus_ai_stage2_test_[a-f0-9]{16}\\?.+");
        assertThat(nonce).isNotBlank();
        var ds =
                new DriverManagerDataSource(
                        url,
                        System.getenv("AI_GOV_TEST_USER"),
                        System.getenv("AI_GOV_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM _codex_test_owner WHERE owner_nonce=?",
                                Integer.class,
                                nonce))
                .isEqualTo(1);
        String schema =
                Files.readString(Path.of("../../script/sql/update/2026-10-22-ai-stage-two.sql"))
                        .replaceAll("(?m)^--.*$", "");
        for (String statement : schema.split(";"))
            if (!statement.isBlank()) jdbc.execute(statement);
        var tables = Pattern.compile("CREATE TABLE IF NOT EXISTS (ai_[a-z0-9_]+)").matcher(schema);
        while (tables.find()) jdbc.execute("TRUNCATE TABLE " + tables.group(1));
        ledger =
                new AiCallLedgerService(
                        ds,
                        Clock.fixed(
                                Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void mysqlDriverLeaseAndAtomicMessageContracts() throws Exception {
        String seed = Files.readString(Path.of("../../script/sql/ry-vue.sql"));
        var definition =
                Pattern.compile(
                                "CREATE TABLE [`]?ai_message[`]?\\s*\\([\\s\\S]*?;",
                                Pattern.CASE_INSENSITIVE)
                        .matcher(seed);
        assertThat(definition.find()).isTrue();
        jdbc.execute(definition.group().replaceFirst("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"));
        var repository = new AiExecutionRepository(jdbc.getDataSource());
        String id = repository.create("000001", 7L, "CHAT", "", 9L, "{}", 600);
        var lease = repository.claim("000001", id, "mysql-node");
        assertThat(lease).isNotNull();
        var message = new AiMessage();
        message.setSessionId(9L);
        message.setRole("ASSISTANT");
        message.setContent("fixture");
        assertThat(
                        repository.completeAtomically(
                                lease,
                                "SUCCEEDED",
                                () -> repository.insertAssistant(lease, message),
                                () -> {}))
                .isEqualTo("SUCCEEDED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM ai_message WHERE id=? AND tenant_id='000001'",
                                Integer.class,
                                message.getId()))
                .isEqualTo(1);
    }
}
