package com.nexus.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.system.domain.SysDicArea;

/**
 * 行政区划Mapper接口
 *
 * @author nexus
 * @date 2025-10-29
 */
public interface SysDicAreaMapper extends BaseMapper<SysDicArea> {
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
     * 删除行政区划
     *
     * @param pkid 行政区划主键
     * @return 结果
     */
    public int deleteSysDicAreaByPkid(Long pkid);

    /**
     * 批量删除行政区划
     *
     * @param pkids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSysDicAreaByPkids(Long[] pkids);
}
