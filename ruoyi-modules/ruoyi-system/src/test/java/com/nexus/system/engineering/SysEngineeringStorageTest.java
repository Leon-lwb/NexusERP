package com.nexus.system.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.ai.engineering.*;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.enums.AccessPolicyType;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.system.domain.SysOss;
import com.nexus.system.mapper.SysOssMapper;
import com.nexus.system.service.ISysOssConfigService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.util.Set;

class SysEngineeringStorageTest {
    @TempDir Path temp;

    @Test
    void coldStartInitializesConfigurationBeforePrivateBucketValidation() {
        var initialized = new java.util.concurrent.atomic.AtomicBoolean(false);
        var configs = mock(ISysOssConfigService.class);
        doAnswer(call -> {
            initialized.set(true);
            return null;
        }).when(configs).init();
        var client = mock(OssClient.class);
        when(client.getAccessPolicy()).thenReturn(AccessPolicyType.PRIVATE);
        try (var factory = mockStatic(OssFactory.class)) {
            factory.when(() -> OssFactory.instance("private-engineering")).thenAnswer(call -> {
                if (!initialized.get()) throw new IllegalStateException("OSS cache is cold");
                return client;
            });
            var adapter = new SysEngineeringStorageAdapter(
                    mock(SysOssMapper.class),
                    mock(com.nexus.common.ai.mapper.AiEngineeringArtifactMapper.class),
                    "private-engineering", configs);
            assertDoesNotThrow(adapter::verifyConfiguration);
            verify(configs).init();
        }
    }

    @Test
    void publicOrImplicitBucketIsRejectedBeforeAnyUpload() {
        var client = mock(OssClient.class);
        var mapper = mock(SysOssMapper.class);
        var artifacts = mock(com.nexus.common.ai.mapper.AiEngineeringArtifactMapper.class);
        try (var factory = mockStatic(OssFactory.class)) {
            factory.when(() -> OssFactory.instance("private-engineering")).thenReturn(client);
            var adapter =
                    new SysEngineeringStorageAdapter(
                            mapper, artifacts, "private-engineering", mock(ISysOssConfigService.class));
            when(client.getAccessPolicy()).thenReturn(AccessPolicyType.PUBLIC);
            assertThrows(IllegalStateException.class, adapter::verifyConfiguration);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            new SysEngineeringStorageAdapter(mapper, artifacts, "", mock(ISysOssConfigService.class))
                                    .verifyConfiguration());
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void objectNamespaceIsGeneratedByServerAndMetadataNeverContainsPublicUrl() throws Exception {
        var client = mock(OssClient.class);
        var mapper = mock(SysOssMapper.class);
        var artifacts = mock(com.nexus.common.ai.mapper.AiEngineeringArtifactMapper.class);
        when(client.getAccessPolicy()).thenReturn(AccessPolicyType.PRIVATE);
        var path = temp.resolve("file.obj");
        Files.writeString(path, "v 0 0 0\n");
        try (var factory = mockStatic(OssFactory.class)) {
            factory.when(() -> OssFactory.instance("private-engineering")).thenReturn(client);
            var adapter =
                    new SysEngineeringStorageAdapter(
                            mapper, artifacts, "private-engineering", mock(ISysOssConfigService.class));
            adapter.verifyConfiguration();
            var reservation = new com.nexus.common.ai.domain.entity.AiEngineeringArtifact();
            reservation.setArtifactId(9L);
            reservation.setOssId(9L);
            reservation.setTenantId("000001");
            reservation.setUserId(7L);
            reservation.setTaskId(8L);
            reservation.setObjectKey("engineering/000001/7/8/9");
            reservation.setService("private-engineering");
            reservation.setSha256("a".repeat(64));
            reservation.setSize(Files.size(path));
            reservation.setStatus("RESERVED");
            when(artifacts.selectById(9L)).thenReturn(reservation);
            var stored =
                    adapter.write(
                            new EngineeringArtifactWrite(
                                    new EngineeringActor("000001", 7L, Set.of()),
                                    8L,
                                    9L,
                                    EngineeringArtifactType.PCB_MODEL_OBJ,
                                    path,
                                    "a".repeat(64),
                                    Files.size(path),
                                    "text/plain",
                                    "board.obj"));
            assertEquals("engineering/000001/7/8/9", stored.objectKey());
            var captor = org.mockito.ArgumentCaptor.forClass(SysOss.class);
            verify(mapper).insert(captor.capture());
            assertEquals("", captor.getValue().getUrl());
            assertEquals("000001", captor.getValue().getTenantId());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            adapter.deleteUnconfirmed(
                                    new EngineeringArtifactStored(
                                            9L,
                                            "private-engineering",
                                            "unrelated/file",
                                            1,
                                            "a".repeat(64))));
        }
    }

    @Test
    void unconfirmedDeletionRequiresPositiveArtifactStateProof() throws Exception {
        var client = mock(OssClient.class);
        var mapper = mock(SysOssMapper.class);
        var artifacts = mock(com.nexus.common.ai.mapper.AiEngineeringArtifactMapper.class);
        when(client.getAccessPolicy()).thenReturn(AccessPolicyType.PRIVATE);
        var metadata = new SysOss();
        metadata.setOssId(9L);
        metadata.setFileName("engineering/000001/7/8/9");
        metadata.setService("private-engineering");
        when(mapper.selectById(9L)).thenReturn(metadata);
        try (var factory = mockStatic(OssFactory.class)) {
            factory.when(() -> OssFactory.instance("private-engineering")).thenReturn(client);
            var adapter =
                    new SysEngineeringStorageAdapter(
                            mapper, artifacts, "private-engineering", mock(ISysOssConfigService.class));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            adapter.deleteUnconfirmed(
                                    new EngineeringArtifactStored(
                                            9L,
                                            "private-engineering",
                                            "engineering/000001/7/8/9",
                                            8,
                                            "a".repeat(64))));
            verify(client, never()).delete(anyString());
            verify(mapper, never()).deleteById(anyLong());
        }
    }

    @ParameterizedTest
    @EnumSource(EngineeringArtifactType.class)
    void privateMetadataFitsTheInstalledOssSchema(EngineeringArtifactType type) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:engineering-oss-" + type + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        // Same suffix budget as the installed sys_oss table; never widen the user's schema.
        jdbc.execute("create table sys_oss(oss_id bigint primary key, file_suffix varchar(10) not null,"
                + " url varchar(500) not null, ext1 varchar(255), service varchar(64))");
        var client = mock(OssClient.class);
        var mapper = mock(SysOssMapper.class);
        var artifacts = mock(com.nexus.common.ai.mapper.AiEngineeringArtifactMapper.class);
        when(client.getAccessPolicy()).thenReturn(AccessPolicyType.PRIVATE);
        when(mapper.insert(any(SysOss.class))).thenAnswer(call -> {
            var metadata = call.getArgument(0, SysOss.class);
            return jdbc.update("insert into sys_oss(oss_id,file_suffix,url,ext1,service) values(?,?,?,?,?)",
                    metadata.getOssId(), metadata.getFileSuffix(), metadata.getUrl(),
                    metadata.getExt1(), metadata.getService());
        });
        var staged = temp.resolve(type + ".validated");
        Files.writeString(staged, "validated synthetic artifact");
        var reservation = new com.nexus.common.ai.domain.entity.AiEngineeringArtifact();
        reservation.setArtifactId(9L);
        reservation.setOssId(9L);
        reservation.setTenantId("000001");
        reservation.setUserId(7L);
        reservation.setTaskId(8L);
        reservation.setObjectKey("engineering/000001/7/8/9");
        reservation.setService("private-engineering");
        reservation.setSha256("a".repeat(64));
        reservation.setSize(Files.size(staged));
        reservation.setStatus("RESERVED");
        when(artifacts.selectById(9L)).thenReturn(reservation);
        try (var factory = mockStatic(OssFactory.class)) {
            factory.when(() -> OssFactory.instance("private-engineering")).thenReturn(client);
            var adapter = new SysEngineeringStorageAdapter(
                            mapper, artifacts, "private-engineering", mock(ISysOssConfigService.class));
            assertDoesNotThrow(() -> adapter.write(new EngineeringArtifactWrite(
                    new EngineeringActor("000001", 7L, Set.of()), 8L, 9L, type, staged,
                    "a".repeat(64), Files.size(staged),
                    type == EngineeringArtifactType.PCB_MODEL_TEXTURE ? "image/png" : "text/plain",
                    "artifact")));
            String suffix = jdbc.queryForObject("select file_suffix from sys_oss where oss_id=9", String.class);
            assertTrue(suffix != null && suffix.matches("\\.[a-z0-9]{1,9}"));
            assertEquals("", jdbc.queryForObject("select url from sys_oss where oss_id=9", String.class));
            assertEquals("ENGINEERING_PRIVATE", jdbc.queryForObject("select ext1 from sys_oss where oss_id=9", String.class));
            assertEquals("private-engineering", jdbc.queryForObject("select service from sys_oss where oss_id=9", String.class));
        }
    }

}
