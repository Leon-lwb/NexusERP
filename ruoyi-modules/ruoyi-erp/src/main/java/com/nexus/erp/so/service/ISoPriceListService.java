package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoPriceListBo;
import com.nexus.erp.so.domain.vo.SoPriceListVo;

import java.util.List;

/**
 * 销售价格表Service接口
 *
 * @author nexus
 */
public interface ISoPriceListService {

    /**
     * 分页查询销售价格表列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售价格表分页列表
     */
    TableDataInfo<SoPriceListVo> selectPageList(SoPriceListBo bo, PageQuery pageQuery);

    /**
     * 查询销售价格表列表
     *
     * @param bo 查询参数
     * @return 销售价格表列表
     */
    List<SoPriceListVo> selectList(SoPriceListBo bo);

    /**
     * 根据ID查询销售价格表详情
     *
     * @param id 销售价格表ID
     * @return 销售价格表详情
     */
    SoPriceListVo selectById(Long id);

    /**
     * 新增销售价格表
     *
     * @param bo 销售价格表业务对象
     * @return 影响行数
     */
    int insertByBo(SoPriceListBo bo);

    /**
     * 修改销售价格表
     *
     * @param bo 销售价格表业务对象
     * @return 影响行数
     */
    int updateByBo(SoPriceListBo bo);

    /**
     * 批量删除销售价格表
     *
     * @param ids 销售价格表ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
