package com.nexus.mes.label.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 标签任务快照编码，固定布局、字段和实际发送字节，避免排队及补打期间内容漂移。
 */
final class LabelPrintSnapshot {

    /**
     * 仅序列化内部快照，雪花 ID 显式转为字符串，不参与接口响应序列化。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LabelPrintSnapshot() {}

    /**
     * 冻结模板布局及修订；不保留模板对象引用。
     */
    static void template(MesLabelPrintLog attempt, MesLabelTemplate template) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Long revision =
                template.getTemplateRevision() == null ? 1L : template.getTemplateRevision();
        snapshot.put(
                "templateId",
                template.getTemplateId() == null ? null : template.getTemplateId().toString());
        snapshot.put("templateCode", template.getTemplateCode());
        snapshot.put("templateRevision", revision);
        snapshot.put("labelType", template.getLabelType());
        snapshot.put("labelLang", template.getLabelLang());
        snapshot.put("widthMm", template.getWidthMm() == null ? 60 : template.getWidthMm());
        snapshot.put("heightMm", template.getHeightMm() == null ? 40 : template.getHeightMm());
        snapshot.put("content", template.getContent());
        attempt.setTemplateRevision(revision);
        attempt.setTemplateSnapshot(json(snapshot));
        attempt.setContentHash(
                hash(attempt.getTemplateSnapshot().getBytes(StandardCharsets.UTF_8)));
        attempt.setSnapshotMode("FROZEN");
    }

    /**
     * 冻结字段字面值，排序保证相同字段域具有稳定快照。
     */
    static void fields(MesLabelPrintLog attempt, Map<String, String> fields) {
        attempt.setFieldSnapshot(json(new TreeMap<>(fields)));
    }

    /**
     * 冻结或核验发送指令及其真实编码字节；已有快照不允许被另一个指令覆盖。
     */
    static void payload(MesLabelPrintLog attempt, String payload, Charset charset) {
        if (payload == null || charset == null) {
            throw new ServiceException("打印指令和字符集不能为空");
        }
        if (attempt.getRenderedPayload() != null
                && (!attempt.getRenderedPayload().equals(payload)
                        || !charset.name().equals(attempt.getPayloadCharset()))) {
            throw new ServiceException("打印指令与冻结内容不一致");
        }
        String payloadHash = hash(payload.getBytes(charset));
        if (StringUtils.isNotBlank(attempt.getPayloadHash())
                && !payloadHash.equals(attempt.getPayloadHash())) {
            throw new ServiceException("打印指令哈希与冻结记录不一致");
        }
        if (StringUtils.isNotBlank(attempt.getTemplateSnapshot())
                && StringUtils.isNotBlank(attempt.getContentHash())
                && !hash(attempt.getTemplateSnapshot().getBytes(StandardCharsets.UTF_8))
                        .equals(attempt.getContentHash())) {
            throw new ServiceException("模板快照哈希与冻结记录不一致");
        }
        attempt.setRenderedPayload(payload);
        attempt.setPayloadCharset(charset.name());
        attempt.setPayloadHash(payloadHash);
        if (StringUtils.isBlank(attempt.getSnapshotMode())) {
            attempt.setSnapshotMode("LEGACY_DIRECT");
            attempt.setRemark("历史直发接口：缺少模板与字段冻结来源，仅冻结本次发送指令；未确认实际出纸");
        }
    }

    /**
     * 排队任务使用独立对象，调用方修改入参不能改写已经提交的内容及审计来源。
     */
    static MesLabelPrintLog copy(MesLabelPrintLog source) {
        MesLabelPrintLog copy = new MesLabelPrintLog();
        BeanUtils.copyProperties(source, copy);
        return copy;
    }

    /**
     * 冻结完整记录，仅保存业务及操作者字段；所有长整型按字符串编码，避免跨工具精度丢失。
     */
    static String recordJson(MesLabelPrintLog record) {
        Map<String, Object> values = new LinkedHashMap<>();
        var bean = new BeanWrapperImpl(record);
        for (String property :
                new String[] {
                    "logId",
                    "tenantId",
                    "snId",
                    "snCode",
                    "templateId",
                    "templateRevision",
                    "templateCode",
                    "templateSnapshot",
                    "fieldSnapshot",
                    "contentHash",
                    "payloadHash",
                    "renderedPayload",
                    "payloadCharset",
                    "printerHost",
                    "printerPort",
                    "printSource",
                    "originalPrintLogId",
                    "reprintReason",
                    "snapshotMode",
                    "operatorId",
                    "operatorName",
                    "remark",
                    "retryCount",
                    "failReason"
                }) {
            Object value = bean.getPropertyValue(property);
            values.put(property, value instanceof Long ? value.toString() : value);
        }
        return json(values);
    }

    /**
     * 恢复不可变记录后仍须校验记录摘要及原载荷摘要，不能相信可变的数据库字段。
     */
    static MesLabelPrintLog readRecord(String frozenRecord) {
        try {
            return MAPPER.readValue(frozenRecord, MesLabelPrintLog.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException("打印任务冻结记录不可读");
        }
    }

    /**
     * 生成快照 JSON；序列化失败时不允许继续发送未冻结内容。
     */
    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("标签快照序列化失败");
        }
    }

    /**
     * 使用 SHA-256 对原始字节取摘要。
     */
    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }
}
