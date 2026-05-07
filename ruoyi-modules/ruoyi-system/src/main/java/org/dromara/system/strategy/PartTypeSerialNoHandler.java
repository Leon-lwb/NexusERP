package org.dromara.system.strategy;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import org.dromara.system.domain.SysAutoCodePart;
import org.dromara.system.domain.SysAutoCodeResult;
import org.dromara.system.domain.enums.CycleMethodEnum;
import org.dromara.system.service.ISysAutoCodeResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 序列号处理器
 *
 * @author wanghuan
 */
@Component
@Order(3)
public class PartTypeSerialNoHandler implements PartTypeTemplate {

    @Autowired
    private ISysAutoCodeResultService iAutoCodeResultService;

    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        if ("Y".equals(sysAutoCodePart.getCycleFlag())) {
            return cycleY(sysAutoCodePart);
        } else {
            return cycleN(sysAutoCodePart);
        }
    }

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

    private String cycleN(SysAutoCodePart sysAutoCodePart) {
        List<SysAutoCodeResult> rs = getAutoCodeResult(sysAutoCodePart.getRuleId(), sysAutoCodePart.getInputCharacter(), CycleMethodEnum.CYCLE_METHOD_OTHER.getCode());
        if (CollUtil.isNotEmpty(rs)) {
            Integer lastSerialNo = rs.get(0).getLastSerialNo();
            return String.format("%0" + sysAutoCodePart.getPartLength() + "d", lastSerialNo + sysAutoCodePart.getSeriaStep());
        } else {
            AutoCodeUtil.threadLocal.set(true);
            return String.format("%0" + sysAutoCodePart.getPartLength() + "d", sysAutoCodePart.getSeriaStartNo());
        }
    }

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
