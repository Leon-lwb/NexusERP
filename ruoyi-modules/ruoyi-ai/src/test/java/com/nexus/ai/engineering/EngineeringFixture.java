package com.nexus.ai.engineering;

import static org.mockito.Mockito.*;

import com.nexus.ai.engineering.config.EngineeringProperties;
import com.nexus.ai.engineering.security.*;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.ai.mapper.AiToolMapper;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class EngineeringFixture {
    final EngineeringProperties properties = new EngineeringProperties();
    final EngineeringIdentityPort identity = mock(EngineeringIdentityPort.class);
    final AiToolMapper tools = mock(AiToolMapper.class);
    final EngineeringActor actor =
            new EngineeringActor(
                    "000001",
                    7L,
                    Set.of(
                            "ai:engineering:device",
                            "ai:engineering:task",
                            "ai:engineering:query",
                            "ai:engineering:download"));
    final Clock clock = Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"), ZoneOffset.UTC);
    final EngineeringRepository repository;
    final EngineeringAccess access;
    final EngineeringDeviceService devices;
    final EngineeringEphemeralStore ephemeral =
            new EngineeringEphemeralStore() {
                final Map<String, String> values = new ConcurrentHashMap<>();

                public void put(String purpose, String token, String body, Duration ttl) {
                    values.put(purpose + EngineeringSecrets.hash(token), body);
                }

                public String consume(String purpose, String token) {
                    return values.remove(purpose + EngineeringSecrets.hash(token));
                }
            };

    EngineeringFixture() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(
                "jdbc:h2:mem:"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        new ResourceDatabasePopulator(new ClassPathResource("engineering/schema-h2.sql"))
                .execute(ds);
        repository = new EngineeringRepository(ds);
        properties.setEnabled(true);
        properties.setOssConfigKey("private-engineering");
        when(identity.resolve("000001", 7L))
                .thenReturn(
                        new EngineeringOwnerStatus(
                                true, true, "password-state", actor.permissions()));
        when(tools.engineeringEnabled(anyString())).thenReturn(1L);
        access = new EngineeringAccess(properties, identity, tools);
        devices = new EngineeringDeviceService(repository, ephemeral, access, properties, clock);
    }

    EngineeringDeviceService.Credentials pair() {
        var code = devices.createPairingCode(actor, Set.of(EngineeringAction.values()));
        return devices.pair(
                new EngineeringDeviceService.PairRequest(
                        "nexus-engineering/1", code.pairingCode(), "本地工作站"));
    }
}
