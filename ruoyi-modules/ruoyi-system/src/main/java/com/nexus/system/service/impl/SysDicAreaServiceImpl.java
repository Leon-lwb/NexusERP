package com.nexus.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.mapper.SysDicAreaMapper;
import com.nexus.system.service.ISysDicAreaService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行政区划 服务实现类，实现 {@link ISysDicAreaService} 接口。属于 System 系统管理模块。
 */
@RequiredArgsConstructor
@Service
public class SysDicAreaServiceImpl implements ISysDicAreaService {

    /** SysDicAreaMapper 数据访问层组件，提供 行政区划 的持久化能力。 */
    private final SysDicAreaMapper baseMapper;

    /**
     * 根据条件查询行政区划列表。
     * @param pkid 主键 ID
     * @return 行政区划对象
     */
    @Override
    public SysDicAreaVo selectSysDicAreaByPkid(Long pkid) {
        return baseMapper.selectVoById(pkid);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectSysDicAreaList(SysDicArea sysDicArea) {
        LambdaQueryWrapper<SysDicArea> wrapper = buildQueryWrapper(sysDicArea);
        wrapper.orderByAsc(SysDicArea::getSort);
        return baseMapper.selectVoList(wrapper);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectSysDicAreaTreeList(SysDicArea sysDicArea) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        if (sysDicArea.getParentAreaId() != null) {
            return areas;
        }
        return buildTree(areas);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectChildrenTreeList(SysDicArea sysDicArea) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        return buildTree(areas);
    }

    /**
     * 根据条件查询行政区划列表。
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectProvinces() {
        List<SysDicArea> provinces = baseMapper.selectProvinces();
        return MapstructUtils.convert(provinces, SysDicAreaVo.class);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param parentAreaId 上级区域 ID
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectNextLevel(Long parentAreaId) {
        List<SysDicArea> nextLevel = baseMapper.selectNextLevel(parentAreaId);
        return MapstructUtils.convert(nextLevel, SysDicAreaVo.class);
    }

    /**
     * 新增行政区划。
     * @param sysDicArea 待新增的行政区划
     * @return 受影响行数
     */
    @Override
    public int insertSysDicArea(SysDicArea sysDicArea) {
        return baseMapper.insert(sysDicArea);
    }

    /**
     * 更新行政区划。
     * @param sysDicArea 待更新的行政区划
     * @return 受影响行数
     */
    @Override
    public int updateSysDicArea(SysDicArea sysDicArea) {
        return baseMapper.updateById(sysDicArea);
    }

    /**
     * 删除记录。
     * @param pkid 待删除的 ID
     * @return 受影响行数
     */
    @Override
    public int deleteSysDicAreaByPkid(Long pkid) {
        return baseMapper.deleteById(pkid);
    }

    /**
     * 删除记录。
     * @param pkids 待删除的 ID 数组
     * @return 受影响行数
     */
    @Override
    public int deleteSysDicAreaByPkids(Long[] pkids) {
        int count = 0;
        for (Long pkid : pkids) {
            count += baseMapper.deleteById(pkid);
        }
        return count;
    }

    private LambdaQueryWrapper<SysDicArea> buildQueryWrapper(SysDicArea area) {
        LambdaQueryWrapper<SysDicArea> wrapper = new LambdaQueryWrapper<>();
        if (area.getAreaId() != null) {
            wrapper.eq(SysDicArea::getAreaId, area.getAreaId());
        }
        if (area.getParentAreaId() != null) {
            wrapper.eq(SysDicArea::getParentAreaId, area.getParentAreaId());
        }
        if (area.getAreaName() != null && !area.getAreaName().isEmpty()) {
            wrapper.like(SysDicArea::getAreaName, area.getAreaName());
        }
        if (area.getLayered() != null) {
            wrapper.eq(SysDicArea::getLayered, area.getLayered());
        }
        return wrapper;
    }

    /**
     * 由扁平列表构建森林。
     * <p>根 = 父节点不在结果集中的节点（而非"层级为 1"），因此：
     * 过滤条件命中任意层级时都不会丢数据；父节点缺失的孤儿节点会升级为根，不会不可见。
     * 单次遍历 + Map 索引，复杂度 O(n)。</p>
     */
    private List<SysDicAreaVo> buildTree(List<SysDicAreaVo> areas) {
        if (CollUtil.isEmpty(areas)) {
            return new ArrayList<>();
        }
        Map<Long, SysDicAreaVo> byAreaId = new LinkedHashMap<>(areas.size());
        for (SysDicAreaVo area : areas) {
            area.setChildren(new ArrayList<>());
            if (area.getAreaId() != null) {
                byAreaId.put(area.getAreaId(), area);
            }
        }
        List<SysDicAreaVo> roots = new ArrayList<>();
        for (SysDicAreaVo area : areas) {
            SysDicAreaVo parent = area.getParentAreaId() == null ? null : byAreaId.get(area.getParentAreaId());
            // 父节点缺失，或脏数据把自己当父节点 → 升级为根，避免节点丢失或自环
            if (parent == null || parent == area) {
                roots.add(area);
            } else {
                parent.getChildren().add(area);
            }
        }
        return roots;
    }
}
