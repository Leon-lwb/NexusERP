package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.station.domain.MesSnBindLog;
import com.nexus.station.domain.dto.StationSnDtos;
import com.nexus.station.mapper.StationSnBindLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工位终端关键件绑定/解绑服务 (M2 契约 C5/C6)
 * <p>
 * 复用 pro_sn.parent_sn_id 父子链承载绑定关系; 绑定/解绑均写 mes_sn_bind_log 留痕。
 * 防环: 绑定时沿父链向上遍历祖先(深度上限 10, 对齐 trace 的 MAX_PARENT_DEPTH),
 * 链上任一节点命中待绑组件即拒绝(A 绑 B 后, B 再绑 A 形成环)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationSnService {

    /** 防环遍历深度上限(与 ProSnServiceImpl.trace 的 MAX_PARENT_DEPTH 对齐) */
    private static final int MAX_PARENT_DEPTH = 10;

    private final ProSnMapper proSnMapper;
    private final StationSnBindLogMapper bindLogMapper;

    /**
     * 关键件批量绑定(C5): 组件挂到父SN下(parent_sn_id/parent_sn_code)。
     *
     * @return {boundQty}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bind(StationSnDtos.BindDto dto) {
        String parentCode = trim(dto.getParentSnCode());
        if (StringUtils.isBlank(parentCode)) {
            throw new ServiceException("parentSnCode 不能为空");
        }
        Set<String> componentCodes = new LinkedHashSet<>();
        if (dto.getComponentSnCodes() != null) {
            dto.getComponentSnCodes().stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .forEach(componentCodes::add);
        }
        if (componentCodes.isEmpty()) {
            throw new ServiceException("componentSnCodes 不能为空");
        }

        ProSn parent = selectByCode(parentCode);
        validateBindable(parent, parentCode);

        int bound = 0;
        for (String code : componentCodes) {
            ProSn component = selectByCode(code);
            validateBindable(component, code);
            if (component.getParentSnId() != null) {
                throw new ServiceException(String.format("组件 SN %s 已绑定到父SN %s, 禁止重复绑定",
                    code, component.getParentSnCode()));
            }
            if (component.getSnId().equals(parent.getSnId())) {
                throw new ServiceException("组件 SN 不能与父SN相同: " + code);
            }
            // 防环: 父链祖先命中任一待绑组件即拒绝
            checkCycle(parent, componentCodes, code);

            proSnMapper.update(null, Wrappers.<ProSn>lambdaUpdate()
                .set(ProSn::getParentSnId, parent.getSnId())
                .set(ProSn::getParentSnCode, parent.getSnCode())
                .eq(ProSn::getSnId, component.getSnId())
                .isNull(ProSn::getParentSnId));
            insertLog("BIND", parent, component, null, null, dto.getOperatorCode());
            bound++;
            log.info("关键件绑定: parent={}, component={}", parentCode, code);
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("boundQty", bound);
        return data;
    }

    /**
     * 关键件解绑/换板(C6): 解绑(parent 置空)留痕; 传 newComponentSnCode 则重绑原父(REBIND 一步换板)。
     *
     * @return 空对象(契约 data 为 {})
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unbind(StationSnDtos.UnbindDto dto) {
        String componentCode = trim(dto.getComponentSnCode());
        String reason = trim(dto.getReason());
        String newCode = trim(dto.getNewComponentSnCode());
        if (StringUtils.isBlank(componentCode)) {
            throw new ServiceException("componentSnCode 不能为空");
        }
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("解绑原因 reason 不能为空");
        }
        ProSn component = selectByCode(componentCode);
        if (component.getParentSnId() == null) {
            throw new ServiceException("组件 SN 未绑定任何父SN: " + componentCode);
        }
        ProSn parent = proSnMapper.selectById(component.getParentSnId());
        String parentCodeBefore = component.getParentSnCode();

        // 1. 解绑
        proSnMapper.update(null, Wrappers.<ProSn>lambdaUpdate()
            .set(ProSn::getParentSnId, null)
            .set(ProSn::getParentSnCode, null)
            .eq(ProSn::getSnId, component.getSnId()));
        insertLog("UNBIND", parent, component, null, reason, dto.getOperatorCode());
        log.info("关键件解绑: parent={}, component={}, reason={}", parentCodeBefore, componentCode, reason);

        // 2. 换板: 新组件重绑到原父SN
        if (StringUtils.isNotBlank(newCode)) {
            ProSn newComponent = selectByCode(newCode);
            validateBindable(newComponent, newCode);
            if (newComponent.getParentSnId() != null) {
                throw new ServiceException(String.format("新组件 SN %s 已绑定到父SN %s",
                    newCode, newComponent.getParentSnCode()));
            }
            if (parent == null) {
                throw new ServiceException("原父SN 不存在: " + parentCodeBefore);
            }
            proSnMapper.update(null, Wrappers.<ProSn>lambdaUpdate()
                .set(ProSn::getParentSnId, parent.getSnId())
                .set(ProSn::getParentSnCode, parent.getSnCode())
                .eq(ProSn::getSnId, newComponent.getSnId())
                .isNull(ProSn::getParentSnId));
            insertLog("REBIND", parent, newComponent, componentCode, reason, dto.getOperatorCode());
            log.info("关键件换板重绑: parent={}, old={}, new={}", parent.getSnCode(), componentCode, newCode);
        }
        return new HashMap<>();
    }

    // ==================== 私有辅助 ====================

    private ProSn selectByCode(String snCode) {
        ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, snCode).last("LIMIT 1"));
        if (sn == null) {
            throw new ServiceException("SN不存在: " + snCode);
        }
        return sn;
    }

    /** 可绑定校验: 状态非报废/发货。 */
    private void validateBindable(ProSn sn, String snCode) {
        if ("SCRAPPED".equals(sn.getSnStatus()) || "SHIPPED".equals(sn.getSnStatus())) {
            throw new ServiceException("SN 状态为 " + sn.getSnStatus() + ", 禁止绑定操作: " + snCode);
        }
    }

    /**
     * 防环校验: 从 parent 沿父链向上(上限 MAX_PARENT_DEPTH), 祖先中出现任一待绑组件即拒绝。
     * 绑定后组件位于父链之上, 若祖先含组件则会构成环。
     */
    private void checkCycle(ProSn parent, Set<String> componentCodes, String currentCode) {
        List<String> hit = new ArrayList<>();
        ProSn cursor = parent;
        for (int depth = 0; depth < MAX_PARENT_DEPTH && cursor != null; depth++) {
            if (cursor.getParentSnCode() != null && componentCodes.contains(cursor.getParentSnCode())) {
                hit.add(cursor.getParentSnCode());
            }
            cursor = cursor.getParentSnId() == null ? null : proSnMapper.selectById(cursor.getParentSnId());
        }
        if (!hit.isEmpty()) {
            throw new ServiceException(String.format(
                "防环校验未通过: 组件 %s 位于父SN %s 的祖先链上(%s), 绑定后将形成环",
                currentCode, parent.getSnCode(), String.join(" <- ", hit)));
        }
    }

    private void insertLog(String action, ProSn parent, ProSn component, String oldComponentCode,
                           String reason, String operatorCode) {
        MesSnBindLog logRow = new MesSnBindLog();
        logRow.setAction(action);
        logRow.setParentSnId(parent != null ? parent.getSnId() : null);
        logRow.setParentSnCode(parent != null ? parent.getSnCode() : null);
        logRow.setComponentSnId(component.getSnId());
        logRow.setComponentSnCode(component.getSnCode());
        logRow.setNewComponentSnCode(oldComponentCode);
        logRow.setReason(reason);
        logRow.setOperatorCode(trim(operatorCode));
        bindLogMapper.insert(logRow);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
