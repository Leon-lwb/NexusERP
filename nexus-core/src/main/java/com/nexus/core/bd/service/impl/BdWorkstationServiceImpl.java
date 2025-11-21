package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.bd.mapper.BdWorkstationMapper;
import com.nexus.core.bd.domain.BdWorkstation;
import com.nexus.core.bd.service.IBdWorkstationService;

/**
 * 工作站Service业务层处理
 *
 * @author nexus
 * @date 2025-11-19
 */
@Service
public class BdWorkstationServiceImpl extends ServiceImpl<BdWorkstationMapper, BdWorkstation> implements IBdWorkstationService {

    @Autowired
    private BdWorkstationMapper bdWorkstationMapper;

    /**
     * 查询工作站
     *
     * @param workstationId 工作站主键
     * @return 工作站
     */
    @Override
    public BdWorkstation selectBdWorkstationByWorkstationId(Long workstationId) {
        return bdWorkstationMapper.selectBdWorkstationByWorkstationId(workstationId);
    }

    /**
     * 查询工作站列表
     *
     * @param bdWorkstation 工作站
     * @return 工作站
     */
    @Override
    public List<BdWorkstation> selectBdWorkstationList(BdWorkstation bdWorkstation) {
        return bdWorkstationMapper.selectBdWorkstationList(bdWorkstation);
    }

    /**
     * 新增工作站
     *
     * @param bdWorkstation 工作站
     * @return 结果
     */
    @Override
    public int insertBdWorkstation(BdWorkstation bdWorkstation) {
                bdWorkstation.setCreateTime(DateUtils.getNowDate());
            return bdWorkstationMapper.insertBdWorkstation(bdWorkstation);
    }

    /**
     * 修改工作站
     *
     * @param bdWorkstation 工作站
     * @return 结果
     */
    @Override
    public int updateBdWorkstation(BdWorkstation bdWorkstation) {
                bdWorkstation.setUpdateTime(DateUtils.getNowDate());
        return bdWorkstationMapper.updateBdWorkstation(bdWorkstation);
    }

    /**
     * 批量删除工作站
     *
     * @param workstationIds 需要删除的工作站主键
     * @return 结果
     */
    @Override
    public int deleteBdWorkstationByWorkstationIds(Long[] workstationIds) {
        return bdWorkstationMapper.deleteBdWorkstationByWorkstationIds(workstationIds);
    }

    /**
     * 删除工作站信息
     *
     * @param workstationId 工作站主键
     * @return 结果
     */
    @Override
    public int deleteBdWorkstationByWorkstationId(Long workstationId) {
        return bdWorkstationMapper.deleteBdWorkstationByWorkstationId(workstationId);
    }
}
