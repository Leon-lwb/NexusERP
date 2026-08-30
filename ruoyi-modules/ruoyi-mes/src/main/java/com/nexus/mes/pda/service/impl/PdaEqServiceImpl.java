package com.nexus.mes.pda.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqInspectionRecord;
import com.nexus.mes.eq.domain.bo.InspectionResultBo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqInspectionRecordMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaEqInspectionVo;
import com.nexus.mes.pda.domain.vo.PdaEqItemVo;
import com.nexus.mes.pda.service.IPdaEqService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * PDA 设备点检作业实现
 * <p>
 * 提交链路委托既有 {@code submitInspection}(不合格自动 FAULT + 触发安灯),
 * 本服务补写 {@code eq_inspection_record} 点检历史(既有实体此前未被调用)。
 */
@DS("mes")
@Service
@RequiredArgsConstructor
public class PdaEqServiceImpl implements IPdaEqService {

    private final IEqEquipmentService eqEquipmentService;
    private final EqEquipmentMapper equipmentMapper;
    private final EqInspectionRecordMapper inspectionRecordMapper;

    @Override
    public TableDataInfo<PdaEqItemVo> todo(String keyword, PageQuery pageQuery) {
        Page<EqEquipment> page = equipmentMapper.selectPage(pageQuery.build(),
            Wrappers.<EqEquipment>lambdaQuery()
                .and(StringUtils.isNotBlank(keyword), w -> w
                    .like(EqEquipment::getEquipmentCode, keyword)
                    .or().like(EqEquipment::getEquipmentName, keyword))
                .orderByAsc(EqEquipment::getEquipmentCode));
        List<PdaEqItemVo> items = new ArrayList<>(page.getRecords().size());
        for (EqEquipment eq : page.getRecords()) {
            PdaEqItemVo item = new PdaEqItemVo();
            item.setEquipmentId(eq.getEquipmentId());
            item.setEquipmentCode(eq.getEquipmentCode());
            item.setEquipmentName(eq.getEquipmentName());
            item.setStatus(eq.getStatus());
            Map<String, Object> last = parseLastInspection(eq.getLastInspection());
            if (last != null) {
                Object t = last.get("inspectTime");
                item.setLastInspectionTime(t == null ? null : String.valueOf(t));
                Object pass = last.get("overallPass");
                item.setOverallPass(pass instanceof Boolean ? (Boolean) pass : null);
            }
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaEqInspectionVo detail(Long equipmentId) {
        EqEquipment eq = equipmentMapper.selectById(equipmentId);
        if (eq == null) {
            throw new ServiceException("设备不存在");
        }
        PdaEqInspectionVo vo = new PdaEqInspectionVo();
        vo.setEquipmentId(eq.getEquipmentId());
        vo.setEquipmentCode(eq.getEquipmentCode());
        vo.setEquipmentName(eq.getEquipmentName());
        vo.setStatus(eq.getStatus());
        vo.setWorkcenterCode(eq.getWorkcenterCode());
        vo.setItems(parseItems(eq.getInspectionItems()));
        vo.setLastInspection(parseLastInspection(eq.getLastInspection()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo submit(Long equipmentId, InspectionResultBo bo) {
        EqEquipment eq = equipmentMapper.selectById(equipmentId);
        if (eq == null) {
            throw new ServiceException("设备不存在");
        }
        // 主链路: 更新设备最近点检 + 不合格置 FAULT + 安灯
        eqEquipmentService.submitInspection(equipmentId, bo);

        // 补写点检历史
        EqInspectionRecord record = new EqInspectionRecord();
        record.setEquipmentId(equipmentId);
        record.setStationCode(eq.getWorkcenterCode());
        record.setInspectorId(LoginHelper.getUserId());
        record.setInspectorName(LoginHelper.getUsername());
        record.setOperatorCode(LoginHelper.getUsername());
        record.setInspectTime(new Date());
        record.setResults(JsonUtils.toJsonString(bo.getResults()));
        record.setOverallPass(Boolean.FALSE.equals(bo.getOverallPass()) ? "N" : "Y");
        record.setRemark(bo.getRemark());
        inspectionRecordMapper.insert(record);
        return MesActionResultVo.ok("点检已提交");
    }

    private List<Map<String, Object>> parseItems(String json) {
        if (StringUtils.isBlank(json) || !JsonUtils.isJsonArray(json)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (cn.hutool.core.lang.Dict dict : JsonUtils.parseArrayMap(json)) {
            items.add(dict);
        }
        return items;
    }

    private Map<String, Object> parseLastInspection(String json) {
        if (StringUtils.isBlank(json) || !JsonUtils.isJsonObject(json)) {
            return null;
        }
        return JsonUtils.parseMap(json);
    }
}
