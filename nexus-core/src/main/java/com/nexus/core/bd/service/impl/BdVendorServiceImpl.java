package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.utils.DateUtils;
import com.nexus.core.bd.domain.BdVendor;
import com.nexus.core.bd.mapper.BdVendorMapper;
import com.nexus.core.bd.service.IBdVendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 供应商Service业务层处理
 *
 * @author nexus
 * @date 2025-09-22
 */
@Service
public class BdVendorServiceImpl extends ServiceImpl<BdVendorMapper, BdVendor> implements IBdVendorService {

    @Autowired
    private BdVendorMapper bdVendorMapper;

    /**
     * 查询供应商
     *
     * @param vendorId 供应商主键
     * @return 供应商
     */
    @Override
    public BdVendor selectBdVendorByVendorId(Long vendorId) {
        return bdVendorMapper.selectBdVendorByVendorId(vendorId);
    }

    /**
     * 查询供应商列表
     *
     * @param bdVendor 供应商
     * @return 供应商
     */
    @Override
    public List<BdVendor> selectBdVendorList(BdVendor bdVendor) {
        return bdVendorMapper.selectBdVendorList(bdVendor);
    }

    /**
     * 新增供应商
     *
     * @param bdVendor 供应商
     * @return 结果
     */
    @Override
    public int insertBdVendor(BdVendor bdVendor) {
                bdVendor.setCreateTime(DateUtils.getNowDate());
            return bdVendorMapper.insertBdVendor(bdVendor);
    }

    /**
     * 修改供应商
     *
     * @param bdVendor 供应商
     * @return 结果
     */
    @Override
    public int updateBdVendor(BdVendor bdVendor) {
                bdVendor.setUpdateTime(DateUtils.getNowDate());
        return bdVendorMapper.updateBdVendor(bdVendor);
    }

    /**
     * 批量删除供应商
     *
     * @param vendorIds 需要删除的供应商主键
     * @return 结果
     */
    @Override
    public int deleteBdVendorByVendorIds(Long[] vendorIds) {
        return bdVendorMapper.deleteBdVendorByVendorIds(vendorIds);
    }

    /**
     * 删除供应商信息
     *
     * @param vendorId 供应商主键
     * @return 结果
     */
    @Override
    public int deleteBdVendorByVendorId(Long vendorId) {
        return bdVendorMapper.deleteBdVendorByVendorId(vendorId);
    }
}
