package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.nexus.mes.pro.domain.MesMbom;
import com.nexus.mes.pro.domain.MesMbomLine;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.domain.bo.MesMbomBo;
import com.nexus.mes.pro.domain.bo.MesMbomLineBo;
import com.nexus.mes.pro.domain.vo.MesMbomLineVo;
import com.nexus.mes.pro.domain.vo.MesMbomVo;
import com.nexus.mes.pro.mapper.MesMbomLineMapper;
import com.nexus.mes.pro.mapper.MesMbomMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.service.IMesMbomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MES制造BOM Service 业务层实现
 * <p>
 * 每个ERP BOM对应1个MBOM(UNIQUE bom_id)。
 * 保存时通过 Bridge 校验工序/工位引用是否存在于ERP;
 * 富集时通过 Bridge 查询ERP物料/工序/工位编码名称。
 * 删除时校验是否有在制工单快照引用。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class MesMbomServiceImpl implements IMesMbomService {

    private final MesMbomMapper baseMapper;
    private final MesMbomLineMapper lineMapper;
    private final ProWorkOrderBomLineMapper woBomLineMapper;
    private final IMesErpBridgeService bridge;

    @Override
    /**
     * 查询
     * @param bo bo
     * @param pageQuery pageQuery
     * @return 分页结果
     */
    public TableDataInfo<MesMbomVo> selectPageList(MesMbomBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<MesMbom> lqw = buildQueryWrapper(bo);
        Page<MesMbomVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        enrichMbomVoList(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    /**
     * 查询
     * @param mbomId MBOMID
     * @return 返回视图对象
     */
    public MesMbomVo selectById(Long mbomId) {
        MesMbomVo vo = baseMapper.selectVoById(mbomId);
        if (vo != null) {
            enrichMbomVo(vo);
            List<MesMbomLineVo> lines = enrichLineVo(lineMapper.selectVoList(
                Wrappers.lambdaQuery(MesMbomLine.class)
                    .eq(MesMbomLine::getMbomId, mbomId)
                    .orderByAsc(MesMbomLine::getSortOrder)));
            // 用路由步骤富集工序/步骤序号信息
            if (vo.getRoutingId() != null) {
                enrichLinesWithRoutingSteps(lines, vo.getRoutingId());
            }
            vo.setLines(lines);
        }
        return vo;
    }

    @Override
    /**
     * 查询
     * @param bomId BOMID
     * @return 返回视图对象
     */
    public MesMbomVo selectByBomId(Long bomId) {
        if (bomId == null) {
            return null;
        }
        MesMbom mbom = baseMapper.selectOne(Wrappers.lambdaQuery(MesMbom.class)
            .eq(MesMbom::getBomId, bomId));
        if (mbom == null) {
            return null;
        }
        return selectById(mbom.getMbomId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 新增
     * @param bo bo
     * @return 受影响行数
     */
    public int insertByBo(MesMbomBo bo) {
        // 校验同一ERP BOM不能重复编制MBOM
        Long exist = baseMapper.selectCount(Wrappers.lambdaQuery(MesMbom.class)
            .eq(MesMbom::getBomId, bo.getBomId()));
        if (exist != null && exist > 0) {
            throw new ServiceException("该ERP BOM已存在对应的MBOM，请在MBOM列表中编辑");
        }
        MesMbom entity = MapstructUtils.convert(bo, MesMbom.class);
        entity.setMbomStatus(entity.getMbomStatus() == null ? "DRAFT" : entity.getMbomStatus());
        int rows = baseMapper.insert(entity);
        if (bo.getLines() != null && !bo.getLines().isEmpty()) {
            validateAndSaveLines(entity.getMbomId(), entity.getBomId(), entity.getRoutingId(), bo.getLines());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新
     * @param bo bo
     * @return 受影响行数
     */
    public int updateByBo(MesMbomBo bo) {
        // 校验bomId不与其他MBOM冲突(排除自身)
        if (bo.getBomId() != null && bo.getMbomId() != null) {
            Long exist = baseMapper.selectCount(Wrappers.lambdaQuery(MesMbom.class)
                .eq(MesMbom::getBomId, bo.getBomId())
                .ne(MesMbom::getMbomId, bo.getMbomId()));
            if (exist != null && exist > 0) {
                throw new ServiceException("该ERP BOM已存在其他MBOM，无法重复关联");
            }
        }
        MesMbom entity = MapstructUtils.convert(bo, MesMbom.class);
        int rows = baseMapper.updateById(entity);
        // 先删后插明细行
        lineMapper.delete(Wrappers.lambdaQuery(MesMbomLine.class)
            .eq(MesMbomLine::getMbomId, entity.getMbomId()));
        if (bo.getLines() != null && !bo.getLines().isEmpty()) {
            validateAndSaveLines(entity.getMbomId(), entity.getBomId(), entity.getRoutingId(), bo.getLines());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除
     * @param ids 批量操作ID集合
     * @return 受影响行数
     */
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        // 校验是否有在制工单快照引用这些MBOM的明细行
        List<MesMbomLine> lines = lineMapper.selectList(Wrappers.lambdaQuery(MesMbomLine.class)
            .in(MesMbomLine::getMbomId, idList));
        if (lines != null && !lines.isEmpty()) {
            List<Long> mbomLineIds = lines.stream()
                .map(MesMbomLine::getMbomLineId).collect(Collectors.toList());
            Long ref = woBomLineMapper.selectCount(Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
                .in(ProWorkOrderBomLine::getMbomLineId, mbomLineIds));
            if (ref != null && ref > 0) {
                throw new ServiceException("所选MBOM已被" + ref + "条工单快照引用，无法删除。如需删除请先处理相关工单。");
            }
            // 删除明细行
            lineMapper.deleteBatchIds(mbomLineIds);
        }
        return baseMapper.deleteBatchIds(idList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * activate 操作
     * @param mbomId MBOMID
     */
    public void activate(Long mbomId) {
        MesMbom mbom = baseMapper.selectById(mbomId);
        if (mbom == null) {
            throw new ServiceException("MBOM不存在");
        }
        mbom.setMbomStatus("ACTIVE");
        baseMapper.updateById(mbom);
    }

    @Override
    /**
     * verify 操作
     * @param mbomId MBOMID
     * @return 返回结果
     */
    public String verify(Long mbomId) {
        MesMbom mbom = baseMapper.selectById(mbomId);
        if (mbom == null) {
            throw new ServiceException("MBOM不存在");
        }
        List<MesMbomLine> lines = lineMapper.selectList(Wrappers.lambdaQuery(MesMbomLine.class)
            .eq(MesMbomLine::getMbomId, mbomId));
        if (lines == null || lines.isEmpty()) {
            return "MBOM明细为空，请添加子项物料";
        }
        // 获取路由步骤(若路由已指定)用于校验routingStepId归属
        Set<Long> validStepIds = null;
        if (mbom.getRoutingId() != null) {
            List<Map<String, Object>> steps = bridge.getRoutingSteps(mbom.getRoutingId());
            if (steps != null) {
                validStepIds = steps.stream()
                    .map(m -> ((Number) m.get("step_id")).longValue())
                    .collect(Collectors.toSet());
            }
        }
        List<String> issues = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            MesMbomLine line = lines.get(i);
            String prefix = "第" + (i + 1) + "行: ";
            if (line.getChildMaterialId() == null) {
                issues.add(prefix + "子项物料ID为空");
            }
            if (line.getQuantityPerParent() == null || line.getQuantityPerParent().signum() <= 0) {
                issues.add(prefix + "用量必须大于0");
            }
            if (line.getRoutingStepId() != null && validStepIds != null && !validStepIds.contains(line.getRoutingStepId())) {
                issues.add(prefix + "工序步骤ID不属于MBOM关联的工艺路线");
            }
        }
        return issues.isEmpty() ? "MBOM校验通过" : String.join("; ", issues);
    }

    // ============ private helpers ============

    /**
     * 校验并保存MBOM明细行(先删后插)
     * 校验内容: routingStepId 归属 MBOM 的 routing_id; childMaterialId/processId/workstationId 存在于 ERP
     */
    private void validateAndSaveLines(Long mbomId, Long bomId, Long routingId, List<MesMbomLineBo> lineBos) {
        // 若路由已指定,获取有效步骤ID集合用于校验
        Set<Long> validStepIds = null;
        if (routingId != null) {
            List<Map<String, Object>> steps = bridge.getRoutingSteps(routingId);
            if (steps != null) {
                validStepIds = steps.stream()
                    .map(m -> ((Number) m.get("step_id")).longValue())
                    .collect(Collectors.toSet());
            }
        }
        for (int i = 0; i < lineBos.size(); i++) {
            MesMbomLineBo lineBo = lineBos.get(i);
            if (lineBo.getChildMaterialId() == null) {
                throw new ServiceException("第" + (i + 1) + "行: 子项物料ID不能为空");
            }
            if (lineBo.getQuantityPerParent() == null || lineBo.getQuantityPerParent().signum() <= 0) {
                throw new ServiceException("第" + (i + 1) + "行: 用量必须大于0");
            }
            if (lineBo.getRoutingStepId() != null && validStepIds != null && !validStepIds.contains(lineBo.getRoutingStepId())) {
                throw new ServiceException("第" + (i + 1) + "行: 工序步骤ID[" + lineBo.getRoutingStepId() + "]不属于当前MBOM关联的工艺路线");
            }
            // 桥接校验物料存在(轻量:仅校验非空ID有效)
            if (bridge.getMaterial(lineBo.getChildMaterialId()) == null) {
                throw new ServiceException("第" + (i + 1) + "行: 子项物料ID[" + lineBo.getChildMaterialId() + "]在ERP中不存在");
            }
        }
        // 全部校验通过后批量插入
        for (int i = 0; i < lineBos.size(); i++) {
            MesMbomLineBo lineBo = lineBos.get(i);
            MesMbomLine entity = MapstructUtils.convert(lineBo, MesMbomLine.class);
            entity.setMbomLineId(null);
            entity.setMbomId(mbomId);
            entity.setBomId(bomId);
            if (entity.getSortOrder() == null) {
                entity.setSortOrder(i + 1);
            }
            lineMapper.insert(entity);
        }
    }

    /**
     * 富集MBOM VO列表(批量加载父项物料/路线)
     */
    private void enrichMbomVoList(List<MesMbomVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (MesMbomVo vo : list) {
            enrichMbomVo(vo);
        }
    }

    /**
     * 富集单个MBOM VO: 查询ERP BOM头/路由头(通过Bridge)
     * 注意: Bridge返回Map的key为SQL列名(snake_case), 非驼峰
     */
    private void enrichMbomVo(MesMbomVo vo) {
        if (vo == null) {
            return;
        }
        // 从ERP获取BOM头信息
        Map<String, Object> bomHeader = bridge.getBomHeader(vo.getBomId());
        if (bomHeader != null) {
            vo.setBomVersion(str(bomHeader.get("bom_version")));
            vo.setParentMaterialCode(str(bomHeader.get("parent_material_code")));
            vo.setParentMaterialName(str(bomHeader.get("parent_material_name")));
        }
        // 从ERP获取路线头信息
        if (vo.getRoutingId() != null) {
            Map<String, Object> routingHeader = bridge.getRoutingHeader(vo.getRoutingId());
            if (routingHeader != null) {
                vo.setRoutingCode(str(routingHeader.get("routing_code")));
                vo.setRoutingName(str(routingHeader.get("routing_name")));
            }
        }
    }

    /**
     * 富集MBOM明细行VO: 查询ERP物料/工位编码名称(通过Bridge)
     * 工序/步骤序号富集由 enrichLinesWithRoutingSteps 单独完成(在selectById中调用)
     */
    private List<MesMbomLineVo> enrichLineVo(List<MesMbomLineVo> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        for (MesMbomLineVo line : lines) {
            // 物料信息
            if (line.getChildMaterialId() != null) {
                Map<String, Object> material = bridge.getMaterialFull(line.getChildMaterialId());
                if (material != null) {
                    line.setChildMaterialCode(str(material.get("material_code")));
                    line.setChildMaterialName(str(material.get("material_name")));
                    line.setChildMaterialSpec(str(material.get("material_spec")));
                }
            }
            // 工位信息
            if (line.getWorkstationId() != null) {
                Map<String, Object> ws = bridge.getWorkstationFull(line.getWorkstationId());
                if (ws != null) {
                    line.setWorkstationCode(str(ws.get("workstation_code")));
                    line.setWorkstationName(str(ws.get("workstation_name")));
                }
            }
        }
        return lines;
    }

    /**
     * 用路由步骤列表富集MBOM行的工序/步骤信息
     * 在selectById中, 已知MBOM的routingId, 一次性加载全部步骤后匹配
     */
    private void enrichLinesWithRoutingSteps(List<MesMbomLineVo> lines, Long routingId) {
        if (lines == null || lines.isEmpty() || routingId == null) {
            return;
        }
        List<Map<String, Object>> steps = bridge.getRoutingSteps(routingId);
        if (steps == null || steps.isEmpty()) {
            return;
        }
        // 构建 stepId -> stepInfo 的索引
        Map<Long, Map<String, Object>> stepIndex = new HashMap<>();
        for (Map<String, Object> step : steps) {
            Object idObj = step.get("step_id");
            if (idObj != null) {
                stepIndex.put(((Number) idObj).longValue(), step);
            }
        }
        for (MesMbomLineVo line : lines) {
            if (line.getRoutingStepId() != null) {
                Map<String, Object> stepInfo = stepIndex.get(line.getRoutingStepId());
                if (stepInfo != null) {
                    line.setRoutingStepSequence(toInt(stepInfo.get("sequence_num")));
                    line.setProcessId(toLong(stepInfo.get("process_id")));
                    line.setProcessCode(str(stepInfo.get("process_code")));
                    line.setProcessName(str(stepInfo.get("process_name")));
                }
            }
        }
    }

    private LambdaQueryWrapper<MesMbom> buildQueryWrapper(MesMbomBo bo) {
        LambdaQueryWrapper<MesMbom> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getBomId() != null, MesMbom::getBomId, bo.getBomId());
        lqw.eq(bo.getRoutingId() != null, MesMbom::getRoutingId, bo.getRoutingId());
        lqw.eq(bo.getMbomStatus() != null, MesMbom::getMbomStatus, bo.getMbomStatus());
        lqw.orderByDesc(MesMbom::getCreateTime);
        return lqw;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer toInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}
