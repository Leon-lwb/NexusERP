package com.nexus.iot.alert.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.alert.domain.bo.IotAlertRecordBo;
import com.nexus.iot.alert.domain.vo.IotAlertNotifyLogVo;
import com.nexus.iot.alert.domain.vo.IotAlertRecordVo;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IoT告警记录 Controller（IOT-ALERT-01）。
 * <p>
 * 权限码：{@code iot:alert:list/query/ack/recover/export}，
 * 种子见 {@code script/sql/update/2026-09-20-iot-alert.sql} 第 5 节（menu_id 6030~6034）。
 * <p>
 * 状态机：ACTIVE --ack--&gt; ACKNOWLEDGED --recover--&gt; RESOLVED（RESOLVED 为终态，非法流转返回业务错误）。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/alert")
public class IotAlertController extends BaseController {

    private final IIotAlertRecordService alertRecordService;

    /**
     * 分页查询告警记录（状态/级别/设备/时间区间筛选）
     */
    @SaCheckPermission("iot:alert:list")
    @GetMapping("/list")
    public TableDataInfo<IotAlertRecordVo> list(IotAlertRecordBo bo, PageQuery pageQuery) {
        return alertRecordService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出告警记录到 Excel
     */
    @SaCheckPermission("iot:alert:export")
    @Log(title = "IoT告警记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(IotAlertRecordBo bo, HttpServletResponse response) {
        List<IotAlertRecordVo> list = alertRecordService.selectList(bo);
        ExcelUtil.exportExcel(list, "IoT告警记录", IotAlertRecordVo.class, response);
    }

    /**
     * 根据主键获取告警详情
     */
    @SaCheckPermission("iot:alert:query")
    @GetMapping("/{alertId}")
    public R<IotAlertRecordVo> getInfo(@PathVariable Long alertId) {
        return R.ok(alertRecordService.selectById(alertId));
    }

    /**
     * 查询某条告警的通知投递留痕（SSE/MES 安灯是否发出、发给了谁）
     */
    @SaCheckPermission("iot:alert:query")
    @GetMapping("/notifyLog/{alertId}")
    public R<List<IotAlertNotifyLogVo>> notifyLog(@PathVariable Long alertId) {
        return R.ok(alertRecordService.selectNotifyLogs(alertId));
    }

    /**
     * 确认告警（ACTIVE → ACKNOWLEDGED）
     */
    @SaCheckPermission("iot:alert:ack")
    @Log(title = "IoT告警记录", businessType = BusinessType.UPDATE)
    @PostMapping("/ack")
    public R<Void> acknowledge(@RequestBody IotAlertRecordBo bo) {
        return toAjax(alertRecordService.acknowledge(bo.getAlertId(), bo.getAckRemark()));
    }

    /**
     * 恢复/关闭告警（ACTIVE|ACKNOWLEDGED → RESOLVED）
     */
    @SaCheckPermission("iot:alert:recover")
    @Log(title = "IoT告警记录", businessType = BusinessType.UPDATE)
    @PostMapping("/recover")
    public R<Void> recover(@RequestBody IotAlertRecordBo bo) {
        return toAjax(alertRecordService.resolve(bo.getAlertId(), bo.getResolveValue(), bo.getResolveRemark()));
    }

}
