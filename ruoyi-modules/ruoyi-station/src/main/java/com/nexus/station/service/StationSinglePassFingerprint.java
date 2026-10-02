package com.nexus.station.service;

import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.CharBuffer;
import com.nexus.common.core.exception.ServiceException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 单件请求原始输入的稳定指纹；不包括会随 ERP 关联变化的解析结果或人员姓名。 */
final class StationSinglePassFingerprint {
    private StationSinglePassFingerprint() { }

    /** 长度前缀区分 NULL、空串及各字段边界，包含租户与固定算法版本。 */
    static String of(String tenant, String sn, String station, long timestamp, String result,
                     Long requestedOperatorId, String requestedOperatorCode, String canonicalDefects) {
        StringBuilder text = new StringBuilder();
        for (String value : new String[] {"STATION_SINGLE_REQUEST_V1", tenant, sn, station,
            Long.toString(timestamp), result, requestedOperatorId == null ? null : requestedOperatorId.toString(),
            requestedOperatorCode, canonicalDefects}) {
            if (value == null) text.append("-1:");
            else text.append(value.length()).append(':').append(value);
        }
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(encoded);
            return HexFormat.of().formatHex(digest.digest());
        } catch (CharacterCodingException malformedInput) {
            // getBytes 默认替换非法代理字符会把不同原始请求合并；这里先拒绝而非替换。
            throw new ServiceException("单件过站原始请求含非法 Unicode 字符");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前运行环境缺少必需的 SHA-256 算法");
        }
    }
}
