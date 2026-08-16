package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmBarcodeBo;
import com.nexus.erp.wm.domain.vo.WmBarcodeVo;

import java.util.Date;
import java.util.List;

/**
 * 条码实例 Service 接口
 */
public interface IWmBarcodeService {

    /**
     * 分页查询条码列表
     *
     * @param bo 条码业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmBarcodeVo> selectPageList(WmBarcodeBo bo, PageQuery pageQuery);

    /**
     * 查询条码列表
     *
     * @param bo 条码业务对象（BO）
     * @return 结果列表
     */
    List<WmBarcodeVo> selectList(WmBarcodeBo bo);

    /**
     * 查询条码
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmBarcodeVo selectById(Long id);

    /**
     * 新增条码
     *
     * @param bo 条码业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmBarcodeBo bo);

    /**
     * 修改条码
     *
     * @param bo 条码业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmBarcodeBo bo);

    /**
     * 删除条码
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 生成条码并保存为实例记录
     *
     * @return 生成后的条码值
     */
    String generateAndSave(Long configId, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Long snId, Date bizDate);

    /**
     * 按配置编码生成条码并保存为实例记录
     *
     * @return 生成后的条码值
     */
    String generateAndSaveByCode(String configCode, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Long snId, Date bizDate);
}
