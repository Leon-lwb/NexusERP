package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.domain.vo.SoContractVo;

import java.util.List;

/**
 * 销售合同Service接口
 *
 * @author nexus
 * @date 2026-06-14
 */
public interface ISoContractService {

    /**
     * 分页查询销售合同列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售合同分页列表
     */
    TableDataInfo<SoContractVo> selectPageList(SoContractBo bo, PageQuery pageQuery);

    /**
     * 查询销售合同列表（不分页）
     *
     * @param bo 查询参数
     * @return 销售合同列表
     */
    List<SoContractVo> selectList(SoContractBo bo);

    /**
     * 根据ID查询销售合同
     *
     * @param id 合同ID
     * @return 销售合同详细信息
     */
    SoContractVo selectById(Long id);

    /**
     * 新增销售合同
     *
     * @param bo 销售合同业务对象
     * @return 影响行数
     */
    int insertByBo(SoContractBo bo);

    /**
     * 修改销售合同
     *
     * @param bo 销售合同业务对象
     * @return 影响行数
     */
    int updateByBo(SoContractBo bo);

    /**
     * 批量删除销售合同
     *
     * @param ids 合同ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
