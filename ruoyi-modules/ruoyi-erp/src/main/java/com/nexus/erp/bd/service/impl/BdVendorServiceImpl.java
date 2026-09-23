package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.bd.domain.bo.BdVendorBo;
import com.nexus.erp.bd.domain.vo.BdVendorVo;
import com.nexus.erp.bd.mapper.BdVendorMapper;
import com.nexus.erp.bd.service.IBdVendorService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 供应商信息Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdVendorServiceImpl implements IBdVendorService {

    private final BdVendorMapper baseMapper;
    private final AutoCodeUtil autoCodeUtil;
    /**
     * 分页查询供应商列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @param pageQuery 分页参数，包含页码、每页大小及排序信息
     * @return 分页结果
     */

    @Override
    public TableDataInfo<BdVendorVo> selectPageList(BdVendorBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdVendor> lqw = buildQueryWrapper(bo);
        Page<BdVendorVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }
    /**
     * 查询供应商列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 结果列表
     */

    @Override
    public List<BdVendorVo> selectList(BdVendorBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }
    /**
     * 根据主键查询供应商详情
     *
     * @param id 主键ID
     * @return 结果
     */

    @Override
    public BdVendorVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }
    /**
     * 新增供应商
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdVendorBo bo) {
        BdVendor entity = MapstructUtils.convert(bo, BdVendor.class);
        if (StringUtils.isBlank(entity.getVendorCode())) {
            entity.setVendorCode(autoCodeUtil.genSerialCode("VENDOR_CODE", null));
        }
        return baseMapper.insert(entity);
    }
    /**
     * 修改供应商
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdVendorBo bo) {
        BdVendor entity = MapstructUtils.convert(bo, BdVendor.class);
        return baseMapper.updateById(entity);
    }
    /**
     * 批量删除供应商
     *
     * @param ids 主键ID数组（支持批量）
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdVendor> buildQueryWrapper(BdVendorBo bo) {
        LambdaQueryWrapper<BdVendor> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), BdVendor::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), BdVendor::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getIndustry()), BdVendor::getIndustry, bo.getIndustry());
        lqw.eq(StringUtils.isNotBlank(bo.getRegion()), BdVendor::getRegion, bo.getRegion());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), BdVendor::getStatus, bo.getStatus());
        lqw.eq(bo.getDeptId() != null, BdVendor::getDeptId, bo.getDeptId());
        return lqw;
    }
}
