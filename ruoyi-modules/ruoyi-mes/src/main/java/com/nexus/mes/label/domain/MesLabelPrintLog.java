package com.nexus.mes.label.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 标签打印留痕对象 mes_label_print_log (MES-LABEL-02)
 * <p>每次发送尝试追加一条，记录冻结内容、来源、原任务和补打原因；TCP 发送不能证明实际出纸。
 * 同一 (sn_id, template_id) 的已发送记录用于造码联动去重，
 * 重打必须走显式补打入口(print_source=REPRINT)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_label_print_log")
public class MesLabelPrintLog extends TenantEntity {

    /** 结果-已通过 TCP 发送，未获得打印机确认，不代表实际出纸 */
    public static final String RESULT_SENT = "SENT";

    /** 历史结果-原成功发送记录，仅用于兼容读取，不代表打印机确认 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    /** 结果-失败 */
    public static final String RESULT_FAIL = "FAIL";
    /** 发送是否到达打印机未知，只能核实后显式补打。 */
    public static final String RESULT_UNKNOWN = "UNKNOWN";

    /** 来源-造码联动自动打印 */
    public static final String SOURCE_AUTO = "AUTO";
    /** 来源-人工补打 */
    public static final String SOURCE_REPRINT = "REPRINT";
    /** 来源-模板测试打印 */
    public static final String SOURCE_TEST = "TEST";

    /** 打印日志ID(主键) */
    @TableId(value = "log_id")
    private Long logId;

    /** SN ID(造码打印场景; 模板测试打印为空) */
    private Long snId;

    /** SN编码 */
    private String snCode;

    /** 模板ID(mes_label_template.template_id) */
    private Long templateId;

    /** 冻结的模板修订号；历史记录可能为空 */
    private Long templateRevision;

    /** 冻结的模板布局、语言、尺寸及修订 JSON */
    private String templateSnapshot;

    /** 冻结的 SN、物料、工单等字段 JSON */
    private String fieldSnapshot;

    /** 模板快照 UTF-8 字节的 SHA-256 */
    private String contentHash;

    /** 实际发送编码字节的 SHA-256 */
    private String payloadHash;

    /** 冻结的完整 TSPL/ZPL 指令；补打不得用当前内容替换 */
    private String renderedPayload;

    /** 实际发送字符集名称 */
    private String payloadCharset;

    /** 补打所引用的原打印任务 ID；历史兼容接口可能为空 */
    private Long originalPrintLogId;

    /** 人工补打原因；显式补打必填，不补造旧接口缺失的原因 */
    private String reprintReason;

    /** 快照模式：FROZEN、LEGACY_REQUEST、LEGACY_RECORD、LEGACY_DIRECT */
    private String snapshotMode;

    /** 模板编码(快照, 模板改名/删除后仍可追溯) */
    private String templateCode;

    /** 打印机地址(快照) */
    private String printerHost;

    /** 打印机端口(快照) */
    private Integer printerPort;

    /** 打印时间(尝试发送的时刻) */
    private Date printTime;

    /** 结果：SENT/FAIL；旧 SUCCESS 仅表示历史发送成功 */
    private String result;

    /** 失败原因(渲染失败/打印机不在线/未配置地址等) */
    private String failReason;

    /** 同 SN、同模板的历史发送尝试数；计数查询失败时为空，不伪装成首次打印。 */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private Integer retryCount;

    /** 打印来源: AUTO(造码联动)/REPRINT(补打)/TEST(测试打印) */
    private String printSource;

    /** 操作人ID(登录态; 事件通道为空) */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 备注 */
    private String remark;

    /**
     * 构造打印留痕对象(不含操作人/租户, 由 LabelPrintService 补齐)
     *
     * @param snId         SN ID(可空)
     * @param snCode       SN 编码(可空)
     * @param templateId   模板ID
     * @param templateCode 模板编码
     * @param printerHost  打印机地址
     * @param printerPort  打印机端口
     * @param printSource  打印来源
     */
    public static MesLabelPrintLog of(Long snId, String snCode, Long templateId, String templateCode,
                                      String printerHost, Integer printerPort, String printSource) {
        MesLabelPrintLog printLog = new MesLabelPrintLog();
        printLog.setSnId(snId);
        printLog.setSnCode(snCode);
        printLog.setTemplateId(templateId);
        printLog.setTemplateCode(templateCode);
        printLog.setPrinterHost(printerHost);
        printLog.setPrinterPort(printerPort);
        printLog.setPrintSource(printSource);
        printLog.setRetryCount(0);
        return printLog;
    }
}
