package com.nexus.mes.label.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 标签打印留痕对象 mes_label_print_log (MES-LABEL-02)
 * <p>每一次打印尝试(成功/失败)落一条: 可追溯"一个 SN 打了几张、哪个模板、打在哪台打印机、第几次重打、失败原因";
 * 同一 (sn_id, template_id) 的成功记录即造码联动的<b>幂等键</b> —— 事件重放/重复造码不会重复出纸,
 * 重打必须走显式补打入口(print_source=REPRINT)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_label_print_log")
public class MesLabelPrintLog extends TenantEntity {

    /** 结果-成功 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    /** 结果-失败 */
    public static final String RESULT_FAIL = "FAIL";

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

    /** 模板编码(快照, 模板改名/删除后仍可追溯) */
    private String templateCode;

    /** 打印机地址(快照) */
    private String printerHost;

    /** 打印机端口(快照) */
    private Integer printerPort;

    /** 打印时间(尝试发送的时刻) */
    private Date printTime;

    /** 结果: SUCCESS/FAIL */
    private String result;

    /** 失败原因(渲染失败/打印机不在线/未配置地址等) */
    private String failReason;

    /** 重打计数: 同一 (sn_id, template_id) 的历史打印次数, 首次打印为 0 */
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
