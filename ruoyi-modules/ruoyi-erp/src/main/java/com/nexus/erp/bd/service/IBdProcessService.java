package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdProcessBo;
import com.nexus.erp.bd.domain.vo.BdProcessVo;

import java.util.List;

/**
 * 工序定义Service接口
 *
 * @author nexus
 */
public interface IBdProcessService {

    /**
     * 分页查询工序列表
     */
    TableDataInfo<BdProcessVo> selectPageList(BdProcessBo bo, PageQuery pageQuery);

    /**
     * 查询工序列表
     */
    List<BdProcessVo> selectList(BdProcessBo bo);

    /**
     * 根据ID查询工序
     */
    BdProcessVo selectById(Long id);

    /**
     * 新增工序
     */
    int insertByBo(BdProcessBo bo);

    /**
     * 修改工序
     */
    int updateByBo(BdProcessBo bo);

    /**
     * 批量删除工序
     */
    int deleteByIds(Long[] ids);
}
