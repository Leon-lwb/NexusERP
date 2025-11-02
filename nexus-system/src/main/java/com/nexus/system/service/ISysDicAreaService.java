package com.nexus.system.service;

import java.util.List;
import com.nexus.system.domain.SysDicArea;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 行政区划Service接口
 *
 * @author nexus
 * @date 2025-10-29
 */
public interface ISysDicAreaService extends IService<SysDicArea> {
    /**
     * 查询行政区划
     *
     * @param pkid 行政区划主键
     * @return 行政区划
     */
    public SysDicArea selectSysDicAreaByPkid(Long pkid);

    /**
     * 查询行政区划列表
     *
     * @param sysDicArea 行政区划
     * @return 行政区划集合
     */
    public List<SysDicArea> selectSysDicAreaList(SysDicArea sysDicArea);

    /**
     * 查询行政区划树形列表
     *
     * @param sysDicArea 行政区划
     * @return 行政区划树形集合
     */
    public List<SysDicArea> selectSysDicAreaTreeList(SysDicArea sysDicArea);

    /**
     * 新增行政区划
     *
     * @param sysDicArea 行政区划
     * @return 结果
     */
    public int insertSysDicArea(SysDicArea sysDicArea);

    /**
     * 修改行政区划
     *
     * @param sysDicArea 行政区划
     * @return 结果
     */
    public int updateSysDicArea(SysDicArea sysDicArea);

    /**
     * 批量删除行政区划
     *
     * @param pkids 需要删除的行政区划主键集合
     * @return 结果
     */
    public int deleteSysDicAreaByPkids(Long[] pkids);

    /**
     * 删除行政区划信息
     *
     * @param pkid 行政区划主键
     * @return 结果
     */
    public int deleteSysDicAreaByPkid(Long pkid);

    /**
     * 查询子级行政区划树形列表
     *
     * @param sysDicArea 行政区划
     * @return 树形列表
     */
    List<SysDicArea> selectChildrenTreeList(SysDicArea sysDicArea);


    /**
     * 获取所有省份
     *
     * @return 省份列表
     */
    List<SysDicArea> selectProvinces();

    /**
     * 获取下一级
     *
     * @param parentAreaId 父级ID
     * @return 下一级列表
     */
    List<SysDicArea> selectNextLevel(Long parentAreaId);
}