package com.nexus.mes.ncr.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.ncr.domain.NcrDefectCode;
import com.nexus.mes.ncr.domain.bo.NcrDefectCodeBo;
import com.nexus.mes.ncr.domain.vo.NcrDefectCodeVo;
import com.nexus.mes.ncr.mapper.NcrDefectCodeMapper;
import com.nexus.mes.ncr.mapper.NcrDefectMapper;
import com.nexus.mes.ncr.service.INcrDefectCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MES NCR缺陷代码 Service 业务层实现
 * <p>
 * 缺陷代码采用树形结构,支持按父级分组。查询时返回全部列表并组装为树形,
 * 新增时校验父节点存在,删除时校验孤儿引用。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("ncrDefectCodeService")
public class NcrDefectCodeServiceImpl implements INcrDefectCodeService {

    private final NcrDefectCodeMapper baseMapper;
    private final NcrDefectMapper defectMapper;

    /**
     * 查询缺陷代码列表(树形,返回全部含children)
     * <p>按 parentId 分组,组装为树形结构。</p>
     *
     * @param bo 查询条件
     * @return 缺陷代码树形列表
     */
    @Override
    public List<NcrDefectCodeVo> selectList(NcrDefectCodeBo bo) {
        LambdaQueryWrapper<NcrDefectCode> lqw = buildQueryWrapper(bo);
        List<NcrDefectCodeVo> all = baseMapper.selectVoList(lqw);
        // 组装树形: 按 parentId 分组
        Map<Long, List<NcrDefectCodeVo>> parentMap = all.stream()
            .collect(Collectors.groupingBy(
                vo -> vo.getParentId() != null ? vo.getParentId() : 0L));
        // 为每个节点设置 children
        for (NcrDefectCodeVo vo : all) {
            List<NcrDefectCodeVo> children = parentMap.get(vo.getCodeId());
            if (children != null && !children.isEmpty()) {
                vo.setChildren(children);
            }
        }
        // 返回顶层节点(parentId=0)
        return parentMap.getOrDefault(0L, new ArrayList<>());
    }

    /**
     * 根据ID获取缺陷代码
     *
     * @param codeId 代码ID
     * @return 缺陷代码视图对象
     */
    @Override
    public NcrDefectCodeVo selectById(Long codeId) {
        return baseMapper.selectVoById(codeId);
    }

    /**
     * 新增缺陷代码
     * <p>parentId 非0时校验父节点存在。</p>
     *
     * @param bo 缺陷代码业务对象
     * @return 新增成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(NcrDefectCodeBo bo) {
        // 校验父节点存在
        if (bo.getParentId() != null && bo.getParentId() != 0L) {
            NcrDefectCode parent = baseMapper.selectById(bo.getParentId());
            if (parent == null) {
                throw new RuntimeException("父级缺陷代码不存在: " + bo.getParentId());
            }
        }
        NcrDefectCode entity = new NcrDefectCode();
        entity.setParentId(bo.getParentId() != null ? bo.getParentId() : 0L);
        entity.setCode(bo.getCode());
        entity.setName(bo.getName());
        entity.setLevel(bo.getLevel());
        entity.setEnabled(bo.getEnabled() != null ? bo.getEnabled() : "Y");
        entity.setSortOrder(bo.getSortOrder());
        entity.setRemark(bo.getRemark());
        int rows = baseMapper.insert(entity);
        log.info("新增缺陷代码: code={}, name={}, parentId={}", bo.getCode(), bo.getName(), bo.getParentId());
        return rows > 0;
    }

    /**
     * 修改缺陷代码
     *
     * @param bo 缺陷代码业务对象
     * @return 修改成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(NcrDefectCodeBo bo) {
        NcrDefectCode entity = baseMapper.selectById(bo.getCodeId());
        if (entity == null) {
            throw new RuntimeException("缺陷代码不存在: " + bo.getCodeId());
        }
        entity.setCode(bo.getCode());
        entity.setName(bo.getName());
        entity.setLevel(bo.getLevel());
        entity.setEnabled(bo.getEnabled());
        entity.setSortOrder(bo.getSortOrder());
        entity.setRemark(bo.getRemark());
        int rows = baseMapper.updateById(entity);
        log.info("修改缺陷代码: codeId={}, code={}", bo.getCodeId(), bo.getCode());
        return rows > 0;
    }

    /**
     * 删除缺陷代码
     * <p>孤儿守卫: 有子代码或被 ncr_defect 引用则禁止删除。</p>
     *
     * @param ids 代码ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = java.util.Arrays.asList(ids);
        // 孤儿守卫: 有子代码
        Long childCount = baseMapper.selectCount(
            Wrappers.<NcrDefectCode>lambdaQuery()
                .in(NcrDefectCode::getParentId, idList));
        if (childCount != null && childCount > 0) {
            throw new RuntimeException("存在子级缺陷代码,不可删除");
        }
        // 孤儿守卫: 被 ncr_defect 引用
        com.nexus.mes.ncr.domain.NcrDefect defectRef = defectMapper.selectOne(
            Wrappers.<com.nexus.mes.ncr.domain.NcrDefect>lambdaQuery()
                .in(com.nexus.mes.ncr.domain.NcrDefect::getDefectCodeId, idList)
                .last("LIMIT 1"));
        if (defectRef != null) {
            throw new RuntimeException("缺陷代码已被NCR缺陷明细引用,不可删除");
        }
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除缺陷代码: count={}", ids.length);
        return rows > 0;
    }

    /**
     * 构建缺陷代码查询条件包装器
     */
    private LambdaQueryWrapper<NcrDefectCode> buildQueryWrapper(NcrDefectCodeBo bo) {
        return Wrappers.<NcrDefectCode>lambdaQuery()
            .eq(bo.getParentId() != null, NcrDefectCode::getParentId, bo.getParentId())
            .eq(StringUtils.isNotBlank(bo.getCode()), NcrDefectCode::getCode, bo.getCode())
            .like(StringUtils.isNotBlank(bo.getName()), NcrDefectCode::getName, bo.getName())
            .eq(StringUtils.isNotBlank(bo.getEnabled()), NcrDefectCode::getEnabled, bo.getEnabled())
            .orderByAsc(NcrDefectCode::getSortOrder)
            .orderByAsc(NcrDefectCode::getCodeId);
    }
}