package com.nexus.core.bd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.bd.domain.BdWorkstation;

/**
 * 工作站Mapper接口
 *
 * @author nexus
 * @date 2025-11-19
 */
public interface BdWorkstationMapper extends BaseMapper<BdWorkstation> {
    /**
     * 查询工作站
     *
     * @param workstationId 工作站主键
     * @return 工作站
     */
    public BdWorkstation selectBdWorkstationByWorkstationId(Long workstationId);

    /**
     * 查询工作站列表
     *
     * @param bdWorkstation 工作站
     * @return 工作站集合
     */
    public List<BdWorkstation> selectBdWorkstationList(BdWorkstation bdWorkstation);

    /**
     * 新增工作站
     *
     * @param bdWorkstation 工作站
     * @return 结果
     */
    public int insertBdWorkstation(BdWorkstation bdWorkstation);

    /**
     * 修改工作站
     *
     * @param bdWorkstation 工作站
     * @return 结果
     */
    public int updateBdWorkstation(BdWorkstation bdWorkstation);

    /**
     * 删除工作站
     *
     * @param workstationId 工作站主键
     * @return 结果
     */
    public int deleteBdWorkstationByWorkstationId(Long workstationId);

    /**
     * 批量删除工作站
     *
     * @param workstationIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBdWorkstationByWorkstationIds(Long[] workstationIds);
}
