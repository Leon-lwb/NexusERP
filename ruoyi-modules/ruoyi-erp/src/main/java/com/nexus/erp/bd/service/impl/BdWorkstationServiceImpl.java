package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.domain.bo.BdWorkstationBo;
import com.nexus.erp.bd.domain.vo.BdWorkstationVo;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
import com.nexus.erp.bd.service.IBdWorkstationService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 工作中心Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdWorkstationServiceImpl implements IBdWorkstationService {

    private final BdWorkstationMapper baseMapper;
    private final AutoCodeUtil autoCodeUtil;
    /**
     * 分页查询工位列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @param pageQuery 分页参数，包含页码、每页大小及排序信息
     * @return 分页结果
     */

    @Override
    public TableDataInfo<BdWorkstationVo> selectPageList(BdWorkstationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdWorkstation> lqw = buildQueryWrapper(bo);
        Page<BdWorkstationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }
    /**
     * 查询工位列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 结果列表
     */

    @Override
    public List<BdWorkstationVo> selectList(BdWorkstationBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }
    /**
     * 根据主键查询工位详情
     *
     * @param id 主键ID
     * @return 结果
     */

    @Override
    public BdWorkstationVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }
    /**
     * 新增工位
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdWorkstationBo bo) {
        BdWorkstation entity = MapstructUtils.convert(bo, BdWorkstation.class);
        if (StringUtils.isBlank(entity.getWorkstationCode())) {
            entity.setWorkstationCode(autoCodeUtil.genSerialCode("WORKSTATION_CODE", null));
        }
        return baseMapper.insert(entity);
    }
    /**
     * 修改工位
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdWorkstationBo bo) {
        BdWorkstation entity = MapstructUtils.convert(bo, BdWorkstation.class);
        return baseMapper.updateById(entity);
    }
    /**
     * 批量删除工位
     *
     * @param ids 主键ID数组（支持批量）
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdWorkstation> buildQueryWrapper(BdWorkstationBo bo) {
        LambdaQueryWrapper<BdWorkstation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getWorkstationCode()), BdWorkstation::getWorkstationCode, bo.getWorkstationCode());
        lqw.like(StringUtils.isNotBlank(bo.getWorkstationName()), BdWorkstation::getWorkstationName, bo.getWorkstationName());
        lqw.eq(bo.getWorkshopId() != null, BdWorkstation::getWorkshopId, bo.getWorkshopId());
        lqw.eq(bo.getStatus() != null, BdWorkstation::getStatus, bo.getStatus());
        return lqw;
    }
}
