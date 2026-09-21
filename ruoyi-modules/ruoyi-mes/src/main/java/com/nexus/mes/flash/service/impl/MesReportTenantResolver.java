package com.nexus.mes.flash.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.domain.bo.TestReportBo;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备上报租户解析器 (MES-FLASH-10)
 * <p>免登录设备上报接口(/mes/flash/report/**)原先把租户硬编码为 000000, 会把非 000000 租户产线的刷写/测试记录写错库,
 * 导致门禁按当前租户过滤时查不到记录、SN 永远"未刷写"、工单卡死。此处改为按<b>设备记录</b>解析租户:
 * 优先按上报的 equipmentId/equipmentCode 反查 eq_equipment.tenant_id, 其次按 SN 反查 pro_sn.tenant_id
 * (查询走 {@link TenantHelper#ignore} 忽略租户条件, 否则免登录线程无租户上下文永远查不到)。
 * 整批解析结果必须唯一: 解析不到或跨租户都拒绝(fail-closed), 不回落任何硬编码租户。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class MesReportTenantResolver {

    private final EqEquipmentMapper equipmentMapper;
    private final ProSnMapper proSnMapper;

    /** 解析刷写上报批次所属租户(设备优先, SN 兜底) */
    public String resolveFlashTenant(List<FlashRecordBo> list) {
        Set<String> tenants = new LinkedHashSet<>();
        for (FlashRecordBo bo : list) {
            String tenantId = resolveEquipmentTenant(bo.getEquipmentId(), bo.getEquipmentCode());
            if (StringUtils.isBlank(tenantId)) {
                tenantId = resolveSnTenant(bo.getSnCode());
            }
            if (StringUtils.isNotBlank(tenantId)) {
                tenants.add(tenantId);
            }
        }
        return singleTenant(tenants);
    }

    /** 解析测试上报批次所属租户(设备编码优先, SN 兜底) */
    public String resolveTestTenant(List<TestReportBo> list) {
        Set<String> tenants = new LinkedHashSet<>();
        for (TestReportBo bo : list) {
            String tenantId = resolveEquipmentTenant(null, bo.getEquipmentCode());
            if (StringUtils.isBlank(tenantId)) {
                tenantId = resolveSnTenant(bo.getSnCode());
            }
            if (StringUtils.isNotBlank(tenantId)) {
                tenants.add(tenantId);
            }
        }
        return singleTenant(tenants);
    }

    /** 按设备记录解析租户(设备ID优先, 其次设备编码) */
    private String resolveEquipmentTenant(Long equipmentId, String equipmentCode) {
        if (equipmentId == null && StringUtils.isBlank(equipmentCode)) {
            return null;
        }
        return TenantHelper.ignore(() -> {
            EqEquipment eq = equipmentId != null ? equipmentMapper.selectById(equipmentId) : null;
            if (eq == null && StringUtils.isNotBlank(equipmentCode)) {
                eq = equipmentMapper.selectOne(Wrappers.<EqEquipment>lambdaQuery()
                    .eq(EqEquipment::getEquipmentCode, equipmentCode.trim())
                    .orderByAsc(EqEquipment::getEquipmentId)
                    .last("LIMIT 1"));
            }
            if (eq == null) {
                log.warn("设备上报租户解析: 设备不存在, 回退按SN解析: equipmentId={}, equipmentCode={}", equipmentId, equipmentCode);
                return null;
            }
            return eq.getTenantId();
        });
    }

    /** 按 SN 记录解析租户 */
    private String resolveSnTenant(String snCode) {
        if (StringUtils.isBlank(snCode)) {
            return null;
        }
        return TenantHelper.ignore(() -> {
            ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
                .eq(ProSn::getSnCode, snCode.trim())
                .orderByAsc(ProSn::getSnId)
                .last("LIMIT 1"));
            return sn == null ? null : sn.getTenantId();
        });
    }

    /** 整批租户必须唯一且可解析, 否则拒绝(fail-closed, 不硬编码 000000) */
    private String singleTenant(Set<String> tenants) {
        if (tenants.isEmpty()) {
            throw new ServiceException("无法解析设备上报数据所属租户: 请确认上报的 equipmentCode/SN 已在本系统注册");
        }
        if (tenants.size() > 1) {
            throw new ServiceException("同一批次不允许跨租户上报, 请按租户拆分后重报: " + String.join(",", tenants));
        }
        return tenants.iterator().next();
    }
}
