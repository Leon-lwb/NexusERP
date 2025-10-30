package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.system.mapper.SysDicAreaMapper;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.service.ISysDicAreaService;

/**
 * 行政区划Service业务层处理
 *
 * @author nexus
 * @date 2025-10-29
 */
@Service
public class SysDicAreaServiceImpl extends ServiceImpl<SysDicAreaMapper, SysDicArea> implements ISysDicAreaService {

    @Autowired
    private SysDicAreaMapper sysDicAreaMapper;

    /**
     * 查询行政区划
     *
     * @param pkid 行政区划主键
     * @return 行政区划
     */
    @Override
    public SysDicArea selectSysDicAreaByPkid(Long pkid) {
        return sysDicAreaMapper.selectSysDicAreaByPkid(pkid);
    }

    /**
     * 查询行政区划列表
     *
     * @param sysDicArea 行政区划
     * @return 行政区划
     */
    @Override
    public List<SysDicArea> selectSysDicAreaList(SysDicArea sysDicArea) {
        return sysDicAreaMapper.selectSysDicAreaList(sysDicArea);
    }

    /**
     * 查询行政区划树形列表
     *
     * @param sysDicArea 行政区划
     * @return 行政区划树形列表
     */
    @Override
    public List<SysDicArea> selectSysDicAreaTreeList(SysDicArea sysDicArea) {
        List<SysDicArea> areas = sysDicAreaMapper.selectSysDicAreaList(sysDicArea);
        return buildTree(areas);
    }

    /**
     * 构建行政区划树形结构
     *
     * @param areas 行政区划列表
     * @return 树形结构列表
     */
    private List<SysDicArea> buildTree(List<SysDicArea> areas) {
        // 创建根节点列表 (layered = 1的节点)
        List<SysDicArea> rootAreas = new ArrayList<>();
        
        // 先找出所有根节点（layered = 1的节点）
        for (SysDicArea area : areas) {
            if (area.getLayered() != null && area.getLayered() == 1) {
                area.setChildren(new ArrayList<>());
                rootAreas.add(area);
            }
        }
        
        // 为每个根节点递归构建子树
        for (SysDicArea rootArea : rootAreas) {
            buildSubTree(rootArea, areas);
        }
        
        return rootAreas;
    }
    
    /**
     * 递归构建子树
     *
     * @param parent 父节点
     * @param areas 所有区域列表
     */
    private void buildSubTree(SysDicArea parent, List<SysDicArea> areas) {
        List<SysDicArea> children = new ArrayList<>();
        
        for (SysDicArea area : areas) {
            // 查找parent的直接子节点
            // 子节点的parentAreaId应该等于父节点的areaId
            // 并且子节点的layered应该比父节点大1
            if (parent.getAreaId() != null && 
                parent.getAreaId().equals(area.getParentAreaId()) &&
                area.getLayered() != null && 
                parent.getLayered() != null && 
                area.getLayered().equals(parent.getLayered() + 1)) {
                area.setChildren(new ArrayList<>());
                children.add(area);
                // 递归构建子节点的子树
                buildSubTree(area, areas);
            }
        }
        
        parent.setChildren(children);
    }

    /**
     * 新增行政区划
     *
     * @param sysDicArea 行政区划
     * @return 结果
     */
    @Override
    public int insertSysDicArea(SysDicArea sysDicArea) {
            return sysDicAreaMapper.insertSysDicArea(sysDicArea);
    }

    /**
     * 修改行政区划
     *
     * @param sysDicArea 行政区划
     * @return 结果
     */
    @Override
    public int updateSysDicArea(SysDicArea sysDicArea) {
        return sysDicAreaMapper.updateSysDicArea(sysDicArea);
    }

    /**
     * 批量删除行政区划
     *
     * @param pkids 需要删除的行政区划主键
     * @return 结果
     */
    @Override
    public int deleteSysDicAreaByPkids(Long[] pkids) {
        return sysDicAreaMapper.deleteSysDicAreaByPkids(pkids);
    }

    /**
     * 删除行政区划信息
     *
     * @param pkid 行政区划主键
     * @return 结果
     */
    @Override
    public int deleteSysDicAreaByPkid(Long pkid) {
        return sysDicAreaMapper.deleteSysDicAreaByPkid(pkid);
    }
}