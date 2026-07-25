package com.nexus.mes.lt.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.bo.LtSnBindingBo;
import com.nexus.mes.lt.domain.vo.LtSnBindingVo;

import java.util.List;

/**
 * MES SN批次绑定 Service 业务层接口
 *
 * @author Nexus
 */
public interface ILtSnBindingService {

    /**
     * 分页查询SN批次绑定列表
     */
    TableDataInfo<LtSnBindingVo> selectPageList(LtSnBindingBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取绑定详情
     */
    LtSnBindingVo selectById(Long bindingId);

    /**
     * 新增SN-批次绑定(校验唯一性: 同SN同物料仅一个绑定)
     */
    Boolean insertByBo(LtSnBindingBo bo);

    /**
     * 批量删除绑定
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 按SN ID查询其批次绑定(追溯用)
     */
    List<LtSnBindingVo> selectBySnId(Long snId);

    /**
     * 按批次ID查询所有SN绑定(追溯用)
     */
    List<LtSnBindingVo> selectByLotId(Long lotId);
}
