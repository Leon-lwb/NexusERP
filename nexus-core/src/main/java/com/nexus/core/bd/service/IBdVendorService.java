package com.nexus.core.bd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.core.bd.domain.BdVendor;

import java.util.List;

/**
 * 供应商Service接口
 *
 * @author nexus
 * @date 2025-09-22
 */
public interface IBdVendorService extends IService<BdVendor> {
    /**
     * 查询供应商
     *
     * @param vendorId 供应商主键
     * @return 供应商
     */
    public BdVendor selectBdVendorByVendorId(Long vendorId);

    /**
     * 查询供应商列表
     *
     * @param bdVendor 供应商
     * @return 供应商集合
     */
    public List<BdVendor> selectBdVendorList(BdVendor bdVendor);

    /**
     * 新增供应商
     *
     * @param bdVendor 供应商
     * @return 结果
     */
    public int insertBdVendor(BdVendor bdVendor);

    /**
     * 修改供应商
     *
     * @param bdVendor 供应商
     * @return 结果
     */
    public int updateBdVendor(BdVendor bdVendor);

    /**
     * 批量删除供应商
     *
     * @param vendorIds 需要删除的供应商主键集合
     * @return 结果
     */
    public int deleteBdVendorByVendorIds(Long[] vendorIds);

    /**
     * 删除供应商信息
     *
     * @param vendorId 供应商主键
     * @return 结果
     */
    public int deleteBdVendorByVendorId(Long vendorId);
}
