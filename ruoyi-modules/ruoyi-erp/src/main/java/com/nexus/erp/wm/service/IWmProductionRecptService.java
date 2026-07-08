package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface IWmProductionRecptService {

    TableDataInfo<WmProductionRecptVo> selectPageList(WmProductionRecptBo bo, PageQuery pageQuery);

    List<WmProductionRecptVo> selectList(WmProductionRecptBo bo);

    WmProductionRecptVo selectById(Long id);

    int insertByBo(WmProductionRecptBo bo);

    int updateByBo(WmProductionRecptBo bo);

    int deleteByIds(Long[] ids);

    void execute(Long recptId);

    /**
     * 创建生产入库单并立即执行（用于工单完工自动入库）
     * <p>
     * 在一次调用内完成"生成单据 + 过账入库"，既保留流程单据痕迹（wm_production_recpt），
     * 又执行库存增加与流水记录。完工入库的幂等由底层 ProductionCompletionStrategy 的
     * hasPosted(woId) 保证，重复调用同一工单会被跳过。
     *
     * @param woId          工单ID
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
    Long createAndExecute(Long woId, Long materialId, String materialCode, String materialName,
                          String materialSpec, BigDecimal quantity,
                          Long warehouseId, String warehouseCode, String warehouseName,
                          Long locationId, String locationCode, String locationName,
                          Date recptDate);
}
