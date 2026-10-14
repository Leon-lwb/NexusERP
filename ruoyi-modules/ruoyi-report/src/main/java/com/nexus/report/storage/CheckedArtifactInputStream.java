package com.nexus.report.storage;

import com.nexus.report.domain.ReportFault;

import java.io.*;
import java.security.*;
import java.util.*;

/** 完整流或索引块必须读取至末尾并核对真实长度与SHA-256。 */
public final class CheckedArtifactInputStream extends FilterInputStream {
    /** 保存当前expected状态或配置。 */
    private final long expected;

    /** 保存实际内容完整性摘要。 */
    private final String checksum;

    /** 保存当前release状态或配置。 */
    private final Runnable release;

    /** 保存当前hash状态或配置。 */
    private final MessageDigest hash;

    /** 保存当前count状态或配置。 */
    private long count;

    /** 保存当前verified状态或配置。 */
    private boolean verified, closed;

    /**
     * 固定可信长度和摘要，关闭会读取剩余块以防只验证局部前缀。
     *
     * @param input 已规范化的参数值
     * @param expected 当前契约的expected配置或状态
     * @param checksum 实际内容的SHA-256校验和
     * @param release 当前契约的release配置或状态
     */
    public CheckedArtifactInputStream(
            InputStream input, long expected, String checksum, Runnable release) {
        super(input);
        if (expected < 0 || checksum == null || !checksum.matches("[a-f0-9]{64}"))
            throw ReportFault.invalid("块校验契约无效");
        this.expected = expected;
        this.checksum = checksum;
        this.release = release;
        try {
            hash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * 单字节读取同样进入完整性检查。
     *
     * @return 当前受控处理的固定结果
     */
    public int read() throws IOException {
        int value = in.read();
        if (value < 0) verify();
        else {
            count++;
            if (count > expected) throw invalid();
            hash.update((byte) value);
        }
        return value;
    }

    /**
     * 批量读取只摘要有效字节，不接受多余数据。
     *
     * @param bytes 保留或实际占用的字节数
     * @param offset 当前契约的offset配置或状态
     * @param length 实际内容长度
     * @return 当前受控处理的固定结果
     */
    public int read(byte[] bytes, int offset, int length) throws IOException {
        int read = in.read(bytes, offset, length);
        if (read < 0) verify();
        else if (read > 0) {
            count += read;
            if (count > expected) throw invalid();
            hash.update(bytes, offset, read);
        }
        return read;
    }

    /** 在末尾精确核对长度和摘要。 */
    private void verify() {
        if (verified) return;
        verified = true;
        if (count != expected || !HexFormat.of().formatHex(hash.digest()).equals(checksum))
            throw invalid();
    }

    /** 关闭时验证余下块，之后释放私有物化生命周期。 */
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            if (!verified) {
                byte[] bytes = new byte[65536];
                while (read(bytes, 0, bytes.length) >= 0) {}
            }
        } finally {
            try {
                in.close();
            } finally {
                release.run();
            }
        }
    }

    /**
     * 不暴露存储请求或用户数据。
     *
     * @return 当前受控处理的固定结果
     */
    private static ReportFault invalid() {
        return new ReportFault("ARTIFACT_INVALID", "产物块完整性校验失败", 409);
    }
}
