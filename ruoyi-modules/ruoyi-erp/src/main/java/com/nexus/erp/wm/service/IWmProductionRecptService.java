package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
/**
 * 生产收货服务接口
 * <p>
 * 定义生产收货的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IWmProductionRecptService {

    /**
     * 分页查询生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmProductionRecptVo> selectPageList(WmProductionRecptBo bo, PageQuery pageQuery);

    /**
     * 查询生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果列表
     */
    List<WmProductionRecptVo> selectList(WmProductionRecptBo bo);

    /**
     * 查询生产收货
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmProductionRecptVo selectById(Long id);

    /**
     * 新增生产收货
     *
     * @param bo 生产收货业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmProductionRecptBo bo);

    /**
     * 修改生产收货
     *
     * @param bo 生产收货业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmProductionRecptBo bo);

    /**
     * 删除生产收货
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行生产收货相关操作
     *
     * @param recptId 收货 ID
     */
    void execute(Long recptId);

    /**
     * 创建生产入库单并立即执行（用于工单完工自动入库）
     * <p>
     * 在一次调用内完成"生成单据 + 过账入库"，既保留流程单据痕迹（wm_production_recpt），
     * 又执行库存增加与流水记录。完工入库的幂等由底层 ProductionCompletionStrategy 的
     * hasPosted(woId) 保证，重复调用同一工单会被跳过。
     * <p>
     * 单据填充：工单编号 woNo、为成品自动生成批次（batch_id/batch_code），与库存记录共用同一批次。
     *
     * @param woId          工单ID
     * @param woNo          工单编号
     * @param materialId    物料ID
     * @param materialCode  物料编码
     * @param materialName  物料名称
     * @param materialSpec  物料规格（可空）
     * @param quantity      入库数量
     * @param warehouseId   仓库ID
     * @param warehouseCode 仓库编码
     * @param warehouseName 仓库名称
     * @param locationId    库位ID（可空）
     * @param locationCode  库位编码（可空）
     * @param locationName  库位名称（可空）
     * @param recptDate     入库日期
     * @return 入库单ID
     */
    Long createAndExecute(Long woId, String woNo, Long materialId, String materialCode, String materialName,
                          String materialSpec, BigDecimal quantity,
                          Long warehouseId, String warehouseCode, String warehouseName,
                          Long locationId, String locationCode, String locationName,
                          /** 生产收货（生产收货相关字段） */
                          Date recptDate);
}
