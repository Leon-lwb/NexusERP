package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.vo.EqHealthRecordVo;
import com.nexus.mes.eq.service.IEqHealthService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备健康评分 Controller（预测性维护）
 * <p>
 * 提供设备最新健康评分分页查询、历史趋势与单台手动重评接口。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/health")
public class EqHealthController extends BaseController {

    private final IEqHealthService healthService;

    /**
     * 分页查询设备最新健康评分(最差优先)
     * <p>权限标识：{@code mes:eq:health:list}</p>
     *
     * @param equipmentName 设备名称(模糊)
     * @param grade         等级过滤(HIGH/MEDIUM/LOW)
     * @param pageQuery     分页参数
     * @return 健康评分分页列表
     */
    @SaCheckPermission("mes:eq:health:list")
    @GetMapping("/list")
    public TableDataInfo<EqHealthRecordVo> list(@RequestParam(required = false) String equipmentName,
                                                @RequestParam(required = false) String grade,
                                                PageQuery pageQuery) {
        return healthService.selectLatestPage(equipmentName, grade, pageQuery);
    }

    /**
     * 查询设备评分历史趋势
     * <p>权限标识：{@code mes:eq:health:query}</p>
     *
     * @param equipmentId 设备ID
     * @param limitDays   最近天数(默认 30)
     * @return 评分记录列表(按日期升序)
     */
    @SaCheckPermission("mes:eq:health:query")
    @GetMapping("/trend/{equipmentId}")
    public R<List<EqHealthRecordVo>> trend(@NotNull(message = "设备ID不能为空") @PathVariable Long equipmentId,
                                           @RequestParam(required = false, defaultValue = "30") Integer limitDays) {
        return R.ok(healthService.selectTrend(equipmentId, limitDays == null ? 30 : limitDays));
    }

    /**
     * 手动重新评估全部设备健康
     * <p>权限标识：{@code mes:eq:health:evaluate}</p>
     * <p>日志记录：title="设备健康-重评"，businessType=UPDATE</p>
     *
     * @return 处理设备数
     */
    @SaCheckPermission("mes:eq:health:evaluate")
    @Log(title = "设备健康-重评", businessType = BusinessType.UPDATE)
    @PostMapping("/evaluate")
    public R<Integer> evaluate() {
        return R.ok(healthService.evaluateAll());
    }
}
