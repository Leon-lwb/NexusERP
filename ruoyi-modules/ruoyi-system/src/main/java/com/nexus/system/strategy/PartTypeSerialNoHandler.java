package com.nexus.system.strategy;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.SysAutoCodeResult;
import com.nexus.system.domain.enums.CycleMethodEnum;
import com.nexus.system.service.ISysAutoCodeResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 序列号处理器
 * <p>根据编码组成生成流水号片段，支持按周期（年/月/日/时/分/自定义字符）循环或永不循环两种模式，
 * 并依据上一次结果递增步长、按组成长度补零。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Component
@Order(3)
public class PartTypeSerialNoHandler implements PartTypeTemplate {

    /** 编码生成结果业务服务，用于查询上一次流水号结果。 */
    @Autowired
    private ISysAutoCodeResultService iAutoCodeResultService;

    /**
     * 根据周期标志生成流水号片段：开启周期走 cycleY，否则走 cycleN。
     *
     * @param sysAutoCodePart 编码组成配置（含 cycleFlag 周期标志）
     * @return 生成的流水号片段（已补零至组成长度）
     */
    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        if ("Y".equals(sysAutoCodePart.getCycleFlag())) {
            return cycleY(sysAutoCodePart);
        } else {
            return cycleN(sysAutoCodePart);
        }
    }

    /**
     * 按周期循环生成流水号：根据周期方法（年/月/日/时/分或自定义字符）确定查询维度，
     * 存在上一次结果则在上次序列号基础上累加步长，否则从起始序号开始，并标记是否需新增结果记录。
     */
    private String cycleY(SysAutoCodePart sysAutoCodePart) {
        String method = sysAutoCodePart.getCycleMethod();
        String param = "";
        if (CycleMethodEnum.CYCLE_METHOD_OTHER.getCode().equals(method)) {
            param = sysAutoCodePart.getInputCharacter();
        } else {
            switch (CycleMethodEnum.getByCode(method)) {
                case CYCLE_METHOD_YEAR:
                    param = DateUtil.format(LocalDateTime.now(), "yyyy");
                    break;
                case CYCLE_METHOD_MONTH:
                    param = DateUtil.format(LocalDateTime.now(), "yyyyMM");
                    break;
                case CYCLE_METHOD_DAY:
                    param = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
                    break;
                case CYCLE_METHOD_HOUR:
                    param = DateUtil.format(LocalDateTime.now(), "yyyyMMddHH");
                    break;
                case CYCLE_METHOD_MINUTE:
                    param = DateUtil.format(LocalDateTime.now(), "yyyyMMddHHmm");
                    break;
                default:
                    break;
            }
        }

        List<SysAutoCodeResult> rs = getAutoCodeResult(sysAutoCodePart.getRuleId(), param, method);
        if (CollUtil.isNotEmpty(rs)) {
            AutoCodeUtil.threadLocal.set(false);
            Integer lastSerialNo = rs.get(0).getLastSerialNo();
            return String.format("%0" + sysAutoCodePart.getPartLength() + "d", lastSerialNo + sysAutoCodePart.getSeriaStep());
        } else {
            AutoCodeUtil.threadLocal.set(true);
            return String.format("%0" + sysAutoCodePart.getPartLength() + "d", sysAutoCodePart.getSeriaStartNo());
        }
    }

    /**
     * 不按周期循环生成流水号：以传入字符为维度查询上一次结果，存在则累加步长，否则从起始序号开始。
     */
    private String cycleN(SysAutoCodePart sysAutoCodePart) {
        List<SysAutoCodeResult> rs = getAutoCodeResult(sysAutoCodePart.getRuleId(), sysAutoCodePart.getInputCharacter(), CycleMethodEnum.CYCLE_METHOD_OTHER.getCode());
        if (CollUtil.isNotEmpty(rs)) {
            AutoCodeUtil.threadLocal.set(false);
            Integer lastSerialNo = rs.get(0).getLastSerialNo();
            return String.format("%0" + sysAutoCodePart.getPartLength() + "d", lastSerialNo + sysAutoCodePart.getSeriaStep());
        } else {
            AutoCodeUtil.threadLocal.set(true);
            return String.format("%0" + sysAutoCodePart.getPartLength() + "d", sysAutoCodePart.getSeriaStartNo());
        }
    }

    /**
     * 按规则 ID 与周期维度（传入字符或生成日期）查询上一次编码结果记录。
     */
    private List<SysAutoCodeResult> getAutoCodeResult(Long ruleId, String param, String cycleMethod) {
        SysAutoCodeResult queryParam = new SysAutoCodeResult();
        queryParam.setRuleId(ruleId);

        if (CycleMethodEnum.CYCLE_METHOD_OTHER.getCode().equals(cycleMethod)) {
            queryParam.setLastInputChar(param);
        } else {
            queryParam.setGenDate(param);
        }
        return iAutoCodeResultService.list(queryParam);
    }
}
