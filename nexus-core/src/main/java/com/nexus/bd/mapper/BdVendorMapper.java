package com.nexus.bd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.bd.domain.BdVendor;

/**
 * 供应商Mapper接口
 *
 * @author nexus
 * @date 2025-09-22
 */
public interface BdVendorMapper extends BaseMapper<BdVendor> {
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
     * 删除供应商
     *
     * @param vendorId 供应商主键
     * @return 结果
     */
    public int deleteBdVendorByVendorId(Long vendorId);

    /**
     * 批量删除供应商
     *
     * @param vendorIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBdVendorByVendorIds(Long[] vendorIds);
}
