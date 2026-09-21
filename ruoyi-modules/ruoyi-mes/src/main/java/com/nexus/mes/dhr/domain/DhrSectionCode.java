package com.nexus.mes.dhr.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DHR 批记录分册编码（10 个，固定且顺序固定）。
 *
 * <p>{@code sort_order} 即本类声明顺序（从 1 开始）。{@code snapshot_hash} 的拼接顺序
 * 也依赖该顺序，<b>不得调整</b>，否则历史批记录的哈希将无法复算。</p>
 *
 * <p>某分册无数据时仍会落一条 {@code item_count=0} 的空分册，保证分册集合完整可校验
 * （缺分册本身就是可检出的篡改信号）。</p>
 *
 * @author Nexus
 */
public final class DhrSectionCode {

    private DhrSectionCode() {
    }

    public static final String WO_INFO = "WO_INFO";
    public static final String PROCESS = "PROCESS";
    public static final String MATERIAL = "MATERIAL";
    public static final String EQUIPMENT = "EQUIPMENT";
    public static final String TOOLING = "TOOLING";
    public static final String SOP = "SOP";
    public static final String QC = "QC";
    public static final String DATA_COLLECT = "DATA_COLLECT";
    public static final String SN_TRACE = "SN_TRACE";
    public static final String NCR = "NCR";

    /** 编码 -> 中文名（LinkedHashMap：顺序即 sort_order） */
    private static final Map<String, String> NAMES;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(WO_INFO, "工单信息");
        names.put(PROCESS, "生产过程");
        names.put(MATERIAL, "物料批次");
        names.put(EQUIPMENT, "设备记录");
        names.put(TOOLING, "工装记录");
        names.put(SOP, "作业指导书");
        names.put(QC, "质量检验");
        names.put(DATA_COLLECT, "数据采集");
        names.put(SN_TRACE, "SN 追溯");
        names.put(NCR, "不合格品处理");
        NAMES = Collections.unmodifiableMap(names);
    }

    /**
     * 固定分册编码（按 sort_order 升序）。
     *
     * @return 不可变列表
     */
    public static List<String> ordered() {
        return List.copyOf(NAMES.keySet());
    }

    /**
     * 分册中文名。
     *
     * @param code 分册编码
     * @return 中文名；未知编码原样返回
     */
    public static String nameOf(String code) {
        return NAMES.getOrDefault(code, code);
    }

    /**
     * 分册序号（1 基）。
     *
     * @param code 分册编码
     * @return 序号；未知编码返回 0
     */
    public static int sortOrderOf(String code) {
        int i = 1;
        for (String c : NAMES.keySet()) {
            if (c.equals(code)) {
                return i;
            }
            i++;
        }
        return 0;
    }
}
