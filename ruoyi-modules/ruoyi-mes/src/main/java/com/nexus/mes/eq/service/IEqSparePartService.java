package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqSparePartBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.domain.vo.EqSpareStockLogVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES备件主数据 Service 业务层接口
 * <p>
 * 含主数据 CRUD + 出入库(原子更新 current_stock, 防超卖) + 库存流水查询。
 *
 * @author Nexus
 */
public interface IEqSparePartService {

    /** 流水类型: 入库 */
    String TXN_IN = "IN";
    /** 流水类型: 出库 */
    String TXN_OUT = "OUT";
    /** 流水类型: 维修领用 */
    String TXN_REPAIR_USE = "REPAIR_USE";

    /**
     * 分页查询备件列表(填充 lowStock 标记)
     */
    TableDataInfo<EqSparePartVo> selectPageList(EqSparePartBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取备件详情
     */
    EqSparePartVo selectById(Long spareId);

    /**
     * 查询备件列表(不分页, 用于导出/下拉)
     */
    List<EqSparePartVo> selectList(EqSparePartBo bo);

    /**
     * 新增备件主数据
     */
    EqSparePartVo insertByBo(EqSparePartBo bo);

    /**
     * 修改备件主数据
     */
    Boolean updateByBo(EqSparePartBo bo);

    /**
     * 批量删除备件(校验库存为0或null)
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 出入库操作(IN 库存+, OUT 库存-防超卖)
     * <p>
     * 写流水 + 原子更新 current_stock。OUT/REPAIR_USE 库存不足抛 ServiceException。
     *
     * @param bo 出入库参数(spareId/transactionType/quantity/refType/refId)
     * @return 变动后库存
     */
    BigDecimal stockTransaction(EqSpareStockBo bo);

    /**
     * 分页查询库存流水
     */
    TableDataInfo<EqSpareStockLogVo> selectStockLogPage(EqSpareStockBo bo, PageQuery pageQuery);

    /**
     * 查询库存流水列表(不分页, 用于导出)
     */
    List<EqSpareStockLogVo> selectStockLogList(EqSpareStockBo bo);

    /**
     * 查询低于安全库存的备件(用于监控大屏/告警)
     */
    List<EqSparePartVo> selectLowStock();
}
