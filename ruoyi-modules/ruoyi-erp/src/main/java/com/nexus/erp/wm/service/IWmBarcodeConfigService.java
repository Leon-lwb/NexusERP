package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmBarcodeConfigBo;
import com.nexus.erp.wm.domain.vo.WmBarcodeConfigVo;

import java.util.List;

/**
 * 条码配置 Service 接口
 */
public interface IWmBarcodeConfigService {

    /**
     * 分页查询条码配置列表
     *
     * @param bo 条码配置业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmBarcodeConfigVo> selectPageList(WmBarcodeConfigBo bo, PageQuery pageQuery);

    /**
     * 查询条码配置列表
     *
     * @param bo 条码配置业务对象（BO）
     * @return 结果列表
     */
    List<WmBarcodeConfigVo> selectList(WmBarcodeConfigBo bo);

    /**
     * 查询条码配置
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmBarcodeConfigVo selectById(Long id);

    /**
     * 新增条码配置
     *
     * @param bo 条码配置业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmBarcodeConfigBo bo);

    /**
     * 修改条码配置
     *
     * @param bo 条码配置业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmBarcodeConfigBo bo);

    /**
     * 删除条码配置
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
