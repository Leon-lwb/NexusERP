package com.nexus.system.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.ai.engineering.*;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.enums.AccessPolicyType;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.system.domain.SysOss;
import com.nexus.system.mapper.SysOssMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.Set;

class SysEngineeringStorageTest {
    @TempDir Path temp;

    @Test
    void publicOrImplicitBucketIsRejectedBeforeAnyUpload() {
        var client = mock(OssClient.class);
        var mapper = mock(SysOssMapper.class);
        var artifacts = mock(com.nexus.common.ai.mapper.AiEngineeringArtifactMapper.class);
        try (var factory = mockStatic(OssFactory.class)) {
            factory.when(() -> OssFactory.instance("private-engineering")).thenReturn(client);
            var adapter =
                    new SysEngineeringStorageAdapter(mapper, artifacts, "private-engineering");
            when(client.getAccessPolicy()).thenReturn(AccessPolicyType.PUBLIC);
            assertThrows(IllegalStateException.class, adapter::verifyConfiguration);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            new SysEngineeringStorageAdapter(mapper, artifacts, "")
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
                    new SysEngineeringStorageAdapter(mapper, artifacts, "private-engineering");
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
                    new SysEngineeringStorageAdapter(mapper, artifacts, "private-engineering");
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
}
