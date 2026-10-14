package com.nexus.report.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.storage.ReportArtifactStore;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** 固定快照每512行或约1MiB建立带摘要的块，实现共享S3范围分页。 */
public final class ReportSnapshotIndexService {
    /**
     * 每块坐标和校验属于服务端索引，不接受客户端范围。
     *
     * @param start 块的起始字节偏移
     * @param end 块的终止字节偏移
     * @param firstRow 块内首行的快照行号
     * @param rows 精确类型的数据行
     * @param checksum 实际内容的SHA-256校验和
     */
    public record Block(long start, long end, long firstRow, int rows, String checksum) {}

    /**
     * 索引与原始快照内容摘要固定绑定。
     *
     * @param formatVersion 契约格式版本
     * @param snapshotChecksum 当前契约的snapshotChecksum配置或状态
     * @param rows 精确类型的数据行
     * @param blocks 当前契约的blocks配置或状态
     */
    public record Index(int formatVersion, String snapshotChecksum, long rows, List<Block> blocks) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param formatVersion 当前受控操作的formatVersion输入
         * @param snapshotChecksum 当前受控操作的snapshotChecksum输入
         * @param rows 当前受控操作的rows输入
         * @param blocks 当前受控操作的blocks输入
         */
        public Index {
            blocks = List.copyOf(blocks);
        }
    }

    /**
     * 数据库登记的索引及完整性。
     *
     * @param index 当前契约的index配置或状态
     * @param sourceChecksum 当前契约的sourceChecksum配置或状态
     * @param checksum 实际内容的SHA-256校验和
     */
    private record Link(String index, String sourceChecksum, String checksum) {}

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前artifacts状态或配置。 */
    private final ReportArtifactStore artifacts;

    /** 保存当前json状态或配置。 */
    private final ObjectMapper json;

    /** 保存当前lastSeek状态或配置。 */
    private volatile long lastSeek;

    /**
     * 建立只供固定快照使用的索引服务。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param artifacts 当前契约的artifacts配置或状态
     * @param json 固定版本的契约正文
     */
    public ReportSnapshotIndexService(
            JdbcTemplate jdbc, ReportArtifactStore artifacts, ObjectMapper json) {
        this.jdbc = jdbc;
        this.artifacts = artifacts;
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /**
     * 供容量验证确认实际定位，普通接口不返回内部字节位置。
     *
     * @return 当前受控处理的固定结果
     */
    public long lastSeekOffset() {
        return lastSeek;
    }

    /**
     * 直接读取相交的校验块，行顺序和TypedValue不重新排序或计算。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param offset 当前契约的offset配置或状态
     * @param size 当前契约的size配置或状态
     * @return 当前受控处理的固定结果
     */
    public List<Map<String, TypedValue>> page(
            String tenant, String reference, long offset, int size) {
        if (offset < 0 || size < 1 || size > 5000) throw ReportFault.invalid("索引分页无效");
        var index = ensure(tenant, reference);
        if (offset >= index.rows()) return List.of();
        var result = new ArrayList<Map<String, TypedValue>>();
        for (var block : index.blocks()) {
            if (block.firstRow() + block.rows() <= offset || block.firstRow() >= offset + size)
                continue;
            lastSeek = block.start();
            try (var input =
                            artifacts.range(
                                    tenant,
                                    reference,
                                    block.start(),
                                    block.end() - block.start(),
                                    block.checksum());
                    var reader =
                            new BufferedReader(
                                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                int row = 0;
                while ((line = reader.readLine()) != null) {
                    long ordinal = block.firstRow() + row++;
                    if (ordinal >= offset && ordinal < offset + size) result.add(parse(line));
                }
                if (row != block.rows()) throw invalid();
            } catch (IOException failure) {
                throw invalid();
            }
        }
        return List.copyOf(result);
    }

    /**
     * 首次建立后续页复用同一固定索引，并发首建只保留唯一登记对象。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    private Index ensure(String tenant, String reference) {
        String checksum = artifacts.info(tenant, reference).checksum();
        var existing = link(tenant, reference);
        if (existing.isPresent()) return read(tenant, existing.get(), checksum);
        var index = build(tenant, reference, checksum);
        Path temporary = artifacts.temporary(tenant, "index");
        String candidate = null;
        boolean registered = false;
        try {
            try (var output = artifacts.output(tenant, temporary)) {
                json.writeValue(output, index);
            }
            candidate = artifacts.commit(tenant, temporary, "index");
            String indexChecksum = artifacts.checksum(tenant, candidate);
            try {
                jdbc.update(
                        "INSERT INTO"
                            + " rpt_snapshot_index(tenant_id,snapshot_ref,index_ref,snapshot_checksum,index_checksum)"
                            + " VALUES(?,?,?,?,?)",
                        tenant,
                        reference,
                        candidate,
                        checksum,
                        indexChecksum);
                registered = true;
                return index;
            } catch (org.springframework.dao.DuplicateKeyException concurrent) {
                return read(tenant, link(tenant, reference).orElseThrow(), checksum);
            }
        } catch (IOException failure) {
            throw invalid();
        } finally {
            artifacts.discard(tenant, temporary);
            if (!registered && candidate != null) artifacts.delete(tenant, candidate);
        }
    }

    /**
     * 按字节确定行边界，CRC不依赖操作系统换行或字符长度。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @param checksum 实际内容的SHA-256校验和
     * @return 当前受控处理的固定结果
     */
    private Index build(String tenant, String reference, String checksum) {
        var blocks = new ArrayList<Block>();
        long position = 0, start = 0, totalRows = 0, firstRow = 0;
        int rows = 0, last = -1;
        var hash = sha();
        try (var input = artifacts.open(tenant, reference)) {
            byte[] bytes = new byte[65536];
            int length;
            while ((length = input.read(bytes)) >= 0) {
                if (length == 0) continue;
                int segment = 0;
                for (int i = 0; i < length; i++) {
                    last = bytes[i] & 255;
                    if (last == 10) {
                        totalRows++;
                        rows++;
                        if (totalRows > 100000) throw ReportFault.capacity();
                        if (rows >= 512 || position + i + 1 - start >= 1048576) {
                            hash.update(bytes, segment, i + 1 - segment);
                            long end = position + i + 1;
                            blocks.add(
                                    new Block(
                                            start,
                                            end,
                                            firstRow,
                                            rows,
                                            HexFormat.of().formatHex(hash.digest())));
                            hash = sha();
                            start = end;
                            firstRow = totalRows;
                            rows = 0;
                            segment = i + 1;
                        }
                    }
                }
                if (segment < length) hash.update(bytes, segment, length - segment);
                position += length;
            }
            if (position > start) {
                if (last != 10) {
                    totalRows++;
                    rows++;
                }
                blocks.add(
                        new Block(
                                start,
                                position,
                                firstRow,
                                rows,
                                HexFormat.of().formatHex(hash.digest())));
            }
            return new Index(1, checksum, totalRows, blocks);
        } catch (IOException failure) {
            throw invalid();
        }
    }

    /**
     * 只读取校验通过且与来源checksum一致的索引。
     *
     * @param tenant 租户隔离标识
     * @param link 当前契约的link配置或状态
     * @param sourceChecksum 当前契约的sourceChecksum配置或状态
     * @return 当前受控处理的固定结果
     */
    private Index read(String tenant, Link link, String sourceChecksum) {
        if (!link.sourceChecksum().equals(sourceChecksum)
                || !artifacts.checksum(tenant, link.index()).equals(link.checksum()))
            throw invalid();
        try (var file = artifacts.materialize(tenant, link.index())) {
            if (Files.size(file.path()) > 65536) throw ReportFault.capacity();
            var index = json.readValue(file.path().toFile(), Index.class);
            if (index.formatVersion() != 1
                    || !sourceChecksum.equals(index.snapshotChecksum())
                    || index.rows() < 0
                    || index.rows() > 100000
                    || index.blocks().size() > 1000) throw invalid();
            long cursor = 0, row = 0;
            for (var block : index.blocks()) {
                if (block.start() != cursor
                        || block.end() <= block.start()
                        || block.firstRow() != row
                        || block.rows() < 1
                        || block.rows() > 512
                        || !block.checksum().matches("[a-f0-9]{64}")) throw invalid();
                cursor = block.end();
                row += block.rows();
            }
            if (row != index.rows()) throw invalid();
            return index;
        } catch (IOException failure) {
            throw invalid();
        }
    }

    /**
     * 根据租户和精确快照引用读取索引，参数始终绑定。
     *
     * @param tenant 租户隔离标识
     * @param reference 私有对象的一次性引用
     * @return 当前受控处理的固定结果
     */
    private Optional<Link> link(String tenant, String reference) {
        return jdbc
                .query(
                        "SELECT index_ref,snapshot_checksum,index_checksum FROM rpt_snapshot_index"
                                + " WHERE tenant_id=? AND snapshot_ref=?",
                        (r, i) -> new Link(r.getString(1), r.getString(2), r.getString(3)),
                        tenant,
                        reference)
                .stream()
                .findFirst();
    }

    /**
     * 保持类型化值规范，未知类型或缺失值结构拒绝读取。
     *
     * @param text 当前契约的text配置或状态
     * @return 当前受控处理的固定结果
     */
    private Map<String, TypedValue> parse(String text) {
        try {
            var raw = json.readValue(text, new TypeReference<Map<String, TypedValue>>() {});
            var result = new LinkedHashMap<String, TypedValue>();
            for (var entry : raw.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) throw invalid();
                result.put(
                        entry.getKey(),
                        TypedValue.of(entry.getValue().type(), entry.getValue().value()));
            }
            return Collections.unmodifiableMap(result);
        } catch (Exception failure) {
            throw invalid();
        }
    }

    /**
     * SHA-256为平台必备算法。
     *
     * @return 当前受控处理的固定结果
     */
    private static MessageDigest sha() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * 不向客户端暴露对象路径或数据内容。
     *
     * @return 当前受控处理的固定结果
     */
    private static ReportFault invalid() {
        return new ReportFault("SNAPSHOT_INVALID", "快照块索引完整性校验失败", 409);
    }
}
