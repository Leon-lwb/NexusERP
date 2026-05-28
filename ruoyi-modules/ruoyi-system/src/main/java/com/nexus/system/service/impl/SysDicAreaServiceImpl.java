package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.mapper.SysDicAreaMapper;
import com.nexus.system.service.ISysDicAreaService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class SysDicAreaServiceImpl implements ISysDicAreaService {

    private final SysDicAreaMapper baseMapper;

    @Override
    public SysDicAreaVo selectSysDicAreaByPkid(Long pkid) {
        return baseMapper.selectVoById(pkid);
    }

    @Override
    public List<SysDicAreaVo> selectSysDicAreaList(SysDicArea sysDicArea) {
        LambdaQueryWrapper<SysDicArea> wrapper = buildQueryWrapper(sysDicArea);
        wrapper.orderByAsc(SysDicArea::getSort);
        return baseMapper.selectVoList(wrapper);
    }

    @Override
    public List<SysDicAreaVo> selectSysDicAreaTreeList(SysDicArea sysDicArea) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        if (sysDicArea.getParentAreaId() != null) {
            return areas;
        }
        return buildTree(areas);
    }

    @Override
    public List<SysDicAreaVo> selectChildrenTreeList(SysDicArea sysDicArea) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        return buildTree(areas);
    }

    @Override
    public List<SysDicAreaVo> selectProvinces() {
        List<SysDicArea> provinces = baseMapper.selectProvinces();
        return MapstructUtils.convert(provinces, SysDicAreaVo.class);
    }

    @Override
    public List<SysDicAreaVo> selectNextLevel(Long parentAreaId) {
        List<SysDicArea> nextLevel = baseMapper.selectNextLevel(parentAreaId);
        return MapstructUtils.convert(nextLevel, SysDicAreaVo.class);
    }

    @Override
    public int insertSysDicArea(SysDicArea sysDicArea) {
        return baseMapper.insert(sysDicArea);
    }

    @Override
    public int updateSysDicArea(SysDicArea sysDicArea) {
        return baseMapper.updateById(sysDicArea);
    }

    @Override
    public int deleteSysDicAreaByPkid(Long pkid) {
        return baseMapper.deleteById(pkid);
    }

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

    private List<SysDicAreaVo> buildTree(List<SysDicAreaVo> areas) {
        List<SysDicAreaVo> roots = areas.stream()
            .filter(a -> a.getLayered() != null && a.getLayered() == 1)
            .peek(a -> a.setChildren(new ArrayList<>()))
            .collect(Collectors.toList());

        for (SysDicAreaVo root : roots) {
            buildSubTree(root, areas);
        }
        return roots;
    }

    private void buildSubTree(SysDicAreaVo parent, List<SysDicAreaVo> areas) {
        List<SysDicAreaVo> children = new ArrayList<>();
        for (SysDicAreaVo area : areas) {
            if (parent.getAreaId() != null
                && parent.getAreaId().equals(area.getParentAreaId())
                && area.getLayered() != null
                && parent.getLayered() != null
                && area.getLayered().equals(parent.getLayered() + 1)) {
                area.setChildren(new ArrayList<>());
                children.add(area);
                buildSubTree(area, areas);
            }
        }
        parent.setChildren(children);
    }
}
