package com.nexus.iot.device.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.bo.IotProductBo;
import com.nexus.iot.device.domain.vo.IotProductVo;

import java.util.List;

/**
 * IoT产品Service接口
 *
 * @author nexus
 */
public interface IIotProductService {

    /**
     * 分页查询产品列表
     */
    TableDataInfo<IotProductVo> selectPageList(IotProductBo bo, PageQuery pageQuery);

    /**
     * 查询产品列表（不分页）
     */
    List<IotProductVo> selectList(IotProductBo bo);

    /**
     * 根据ID查询产品
     */
    IotProductVo selectById(Long id);

    /**
     * 新增产品
     */
    int insertByBo(IotProductBo bo);

    /**
     * 修改产品
     */
    int updateByBo(IotProductBo bo);

    /**
     * 批量删除产品
     */
    int deleteByIds(Long[] ids);
}
