package com.nexus.system.service;

import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;

import java.util.List;

/**
 * 行政区划字典服务接口
 * <p>提供全国省/市/区（县）等多级行政区划字典数据的查询、树形构建与增删改能力。
 * 属于 System 系统管理模块下的数据字典子模块，支撑地址选择等业务场景。</p>
 */
public interface ISysDicAreaService {

    /**
     * 根据条件查询行政区划字典列表。
     *
     * @param sysDicArea 查询条件（可包含名称、级别、上级等过滤项）
     * @return 匹配条件的行政区划视图对象列表
     */
    List<SysDicAreaVo> selectSysDicAreaList(SysDicArea sysDicArea);

    /**
     * 查询行政区划字典的树形列表（含全部层级的层级结构）。
     *
     * @param sysDicArea 查询条件
     * @return 树形结构的行政区划视图对象列表
     */
    List<SysDicAreaVo> selectSysDicAreaTreeList(SysDicArea sysDicArea);

    /**
     * 查询指定行政区划下的下级子节点树形列表。
     *
     * @param sysDicArea 查询条件（含父级标识）
     * @return 子级树形结构的行政区划视图对象列表
     */
    List<SysDicAreaVo> selectChildrenTreeList(SysDicArea sysDicArea);

    /**
     * 根据主键 ID 查询行政区划字典详情。
     *
     * @param pkid 行政区划主键 ID
     * @return 行政区划视图对象
     */
    SysDicAreaVo selectSysDicAreaByPkid(Long pkid);

    /**
     * 查询全部省级行政区划列表。
     *
     * @return 省级行政区划视图对象列表
     */
    List<SysDicAreaVo> selectProvinces();

    /**
     * 根据上级区域 ID 查询其直接下级区域列表。
     *
     * @param parentAreaId 上级区域 ID
     * @return 下级区域视图对象列表
     */
    List<SysDicAreaVo> selectNextLevel(Long parentAreaId);

    /**
     * 新增行政区划字典记录。
     *
     * @param sysDicArea 待新增的行政区划
     * @return 受影响行数
     */
    int insertSysDicArea(SysDicArea sysDicArea);

    /**
     * 更新行政区划字典记录。
     *
     * @param sysDicArea 待更新的行政区划
     * @return 受影响行数
     */
    int updateSysDicArea(SysDicArea sysDicArea);

    /**
     * 根据主键 ID 删除行政区划字典记录。
     *
     * @param pkid 行政区划主键 ID
     * @return 受影响行数
     */
    int deleteSysDicAreaByPkid(Long pkid);

    /**
     * 批量删除行政区划字典记录。
     *
     * @param pkids 待删除的行政区划主键 ID 数组
     * @return 受影响行数
     */
    int deleteSysDicAreaByPkids(Long[] pkids);
}
