package com.nexus.mes.lt.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.bo.LtSnBindingBo;
import com.nexus.mes.lt.domain.vo.LtSnBindingVo;

import java.util.List;

/**
 * MES SN批次绑定 Service 业务层接口
 * <p>属于 MES 批次管理模块，提供 SN-批次绑定关系的查询与维护。</p>
 *
 * @author Nexus
 */
public interface ILtSnBindingService {

    /**
     * 分页查询SN批次绑定列表
     *
     * @param bo         查询条件
     * @param pageQuery  分页参数
     * @return 分页结果
     */
    TableDataInfo<LtSnBindingVo> selectPageList(LtSnBindingBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取绑定详情
     *
     * @param bindingId 绑定ID
     * @return 绑定视图对象
     */
    LtSnBindingVo selectById(Long bindingId);

    /**
     * 新增SN-批次绑定(校验唯一性: 同SN同物料仅一个绑定)
     *
     * @param bo 绑定业务对象
     * @return 新增成功返回 true
     */
    Boolean insertByBo(LtSnBindingBo bo);

    /**
     * 批量删除绑定
     *
     * @param ids 绑定ID数组
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 按SN ID查询其批次绑定(追溯用)
     *
     * @param snId SN ID
     * @return 绑定视图对象列表
     */
    List<LtSnBindingVo> selectBySnId(Long snId);

    /**
     * 按批次ID查询所有SN绑定(追溯用)
     *
     * @param lotId 批次ID
     * @return 绑定视图对象列表
     */
    List<LtSnBindingVo> selectByLotId(Long lotId);
}
