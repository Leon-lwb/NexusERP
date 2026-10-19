package com.nexus.mes.label.service;

import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 有界网络发送及冻结编码回归；不建立实际打印机连接。
 */
class LabelPrintServiceTest {
    private MesLabelPrintLog record() {
        var record = MesLabelPrintLog.of(1L, "SN-1", 5L, "TPL", "synthetic", 9100, "TEST");
        record.setRenderedPayload("FROZEN");
        record.setPayloadCharset("UTF-8");
        return record;
    }

    private LabelPrinterTransport network(SocketChannel channel) throws Exception {
        var network = spy(new LabelPrinterTransport());
        doReturn(channel).when(network).openChannel();
        when(channel.connect(any())).thenReturn(true);
        return network;
    }

    @Test
    void tcpWriteIsSentWithoutFabricatingPrinterAcknowledgement() throws Exception {
        var channel = mock(SocketChannel.class);
        var network = network(channel);
        var bytes = new ByteArrayOutputStream();
        when(channel.write(any(ByteBuffer.class)))
                .thenAnswer(
                        inv -> {
                            ByteBuffer input = inv.getArgument(0);
                            int size = input.remaining();
                            byte[] data = new byte[size];
                            input.get(data);
                            bytes.write(data);
                            return size;
                        });
        assertEquals("SENT", network.send(record()).status());
        assertArrayEquals("FROZEN".getBytes(StandardCharsets.UTF_8), bytes.toByteArray());
        verify(channel, times(1)).connect(any());
        verify(channel, never()).read(any(ByteBuffer.class));
    }

    @Test
    void partialWriteExceptionIsUnknownAndDoesNotReconnect() throws Exception {
        var channel = mock(SocketChannel.class);
        var network = network(channel);
        when(channel.write(any(ByteBuffer.class))).thenThrow(new IOException("partial"));
        assertEquals("UNKNOWN", network.send(record()).status());
        verify(channel, times(1)).connect(any());
    }

    @Test
    void blockedWriteHonorsDeadlineAndBecomesUnknown() throws Exception {
        var channel = mock(SocketChannel.class);
        var network = network(channel);
        when(channel.write(any(ByteBuffer.class))).thenReturn(0);
        doThrow(new SocketTimeoutException())
                .when(network)
                .awaitReady(eq(channel), eq(SelectionKey.OP_WRITE), anyLong());
        assertEquals("UNKNOWN", network.send(record()).status());
        verify(channel, times(1)).connect(any());
    }

    @Test
    void failedConnectionDoesNotWriteAnyBytes() throws Exception {
        var channel = mock(SocketChannel.class);
        var network = network(channel);
        when(channel.connect(any())).thenThrow(new IOException("offline"));
        assertEquals("FAIL", network.send(record()).status());
        verify(channel, never()).write(any(ByteBuffer.class));
    }

    @Test
    void missingHostDoesNotCreateChannel() throws Exception {
        var network = spy(new LabelPrinterTransport());
        var record = record();
        record.setPrinterHost(" ");
        assertEquals("FAIL", network.send(record).status());
        verify(network, never()).openChannel();
    }

    @Test
    void networkIsForbiddenInsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(
                    IllegalStateException.class, () -> new LabelPrinterTransport().send(record()));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void templateFieldsAndPayloadAreSnapshotsRatherThanMutableReferences() {
        var service =
                new LabelPrintService(
                        mock(LabelPrintTaskStore.class), mock(LabelPrintDispatcher.class));
        var template = new MesLabelTemplate();
        template.setTemplateId(5L);
        template.setTemplateCode("TPL");
        template.setContent("ORIGINAL");
        template.setTemplateRevision(7L);
        var record = service.newLog(1L, "SN-1", template, "TEST");
        var fields = new HashMap<String, String>();
        fields.put("snCode", "SN-1");
        service.freezeFields(record, fields);
        LabelPrintSnapshot.payload(record, "FROZEN", StandardCharsets.UTF_8);
        fields.put("snCode", "CHANGED");
        template.setContent("CHANGED");
        assertTrue(record.getTemplateSnapshot().contains("ORIGINAL"));
        assertFalse(record.getFieldSnapshot().contains("CHANGED"));
        assertEquals(7L, record.getTemplateRevision());
        assertThrows(
                Exception.class,
                () -> LabelPrintSnapshot.payload(record, "CHANGED", StandardCharsets.UTF_8));
    }
}
