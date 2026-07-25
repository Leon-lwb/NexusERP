package com.nexus.mes.lt.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.bo.LtLotBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * MES批次 Service 业务层接口
 * <p>
 * 状态机: IN_STOCK -> IN_USE -> CONSUMED
 *                  └-> QUARANTINED -> SCRAPPED
 * QC状态: PENDING -> PASS/FAIL
 *
 * @author Nexus
 */
public interface ILtLotService {

    /** 状态: 在库 */
    String STATUS_IN_STOCK = "IN_STOCK";
    /** 状态: 使用中 */
    String STATUS_IN_USE = "IN_USE";
    /** 状态: 已消耗 */
    String STATUS_CONSUMED = "CONSUMED";
    /** 状态: 已隔离 */
    String STATUS_QUARANTINED = "QUARANTINED";
    /** 状态: 已报废 */
    String STATUS_SCRAPPED = "SCRAPPED";

    /** QC状态: 待检 */
    String QC_STATUS_PENDING = "PENDING";
    /** QC状态: 合格 */
    String QC_STATUS_PASS = "PASS";
    /** QC状态: 不合格 */
    String QC_STATUS_FAIL = "FAIL";

    /**
     * 分页查询批次列表
     */
    TableDataInfo<LtLotVo> selectPageList(LtLotBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取批次详情
     */
    LtLotVo selectById(Long lotId);

    /**
     * 根据批次编码查询
     */
    LtLotVo selectByCode(String lotCode);

    /**
     * 新增批次(自动生成编码,初始状态 IN_STOCK, QC PENDING)
     */
    Boolean insertByBo(LtLotBo bo);

    /**
     * 修改批次(仅 IN_STOCK/IN_USE 状态可编辑)
     */
    Boolean updateByBo(LtLotBo bo);

    /**
     * 批量删除批次(仅 IN_STOCK/SCRAPPED 状态可删)
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 批次隔离(IN_STOCK/IN_USE -> QUARANTINED)
     */
    Boolean quarantine(Long lotId, String remark);

    /**
     * 批次报废(QUARANTINED -> SCRAPPED)
     */
    Boolean scrap(Long lotId, String remark);

    /**
     * 批次放行(QUARANTINED -> IN_STOCK,隔离解除)
     */
    Boolean release(Long lotId);

    /**
     * 更新批次QC状态(PENDING -> PASS/FAIL)
     */
    Boolean updateQcStatus(Long lotId, String qcStatus);

    /**
     * 应用IQC检验结果到关联批次(按 iqc_id 查 lot, 回写 qc_status; FAIL 自动隔离)
     * <p>由 QcIqcServiceImpl.inspect() 完成后调用。仅当 lot.iqc_id 已关联时生效;
     * 全量自动(每条IQC都回写)待收货->lot 联动,见整改计划 (b)/(c)。</p>
     *
     * @param iqcId         IQC检验单ID
     * @param inspectResult QUALIFIED / UNQUALIFIED
     * @return 关联并回写成功返回 true; 未关联批次返回 false
     */
    Boolean applyIqcResult(Long iqcId, String inspectResult);

    /**
     * NCR 触发批次隔离(契约方法: P0-4 nr 模块落地后由 NCR 创建流调用)
     * <p>当前 nr 模块未建,此方法作为入口预留; 实现即 quarantine + remark 拼接 NCR 编号。</p>
     *
     * @param lotId  批次ID
     * @param ncrId  NCR单ID(P0-4 落地后传入)
     * @param reason 隔离原因
     */
    Boolean quarantineByNcr(Long lotId, Long ncrId, String reason);

    /**
     * 校验批次是否可用于进站/领料(契约方法: P0-3 进站出站校验调用)
     * <p>当前 P0-3 未建,此方法作为门禁入口预留。校验: 批次存在 + 物料匹配 + status=IN_STOCK
     * + qc_status=PASS + 剩余数量>0。</p>
     *
     * @param materialId 物料ID(可空, 非空则校验匹配)
     * @param lotId      批次ID
     * @return { usable, reason, lotId, lotCode, remainingQty }
     */
    Map<String, Object> checkLotUsable(Long materialId, Long lotId);

    /**
     * FEFO 推荐: 按到期升序返回可用批次
     *
     * @param materialId 物料ID
     * @param requiredQty 需求数量(传 null 返回全部可用,传值则累加直到满足)
     * @return 推荐批次列表(已过滤 IN_STOCK + QC PASS)
     */
    List<LtLotVo> recommendFefo(Long materialId, BigDecimal requiredQty);

    /**
     * 批次消耗(扣减数量,IN_STOCK->IN_USE,数量归零时->CONSUMED)
     * <p>使用 @Lock4j 防并发</p>
     *
     * @param lotId       批次ID
     * @param consumedQty 消耗数量
     * @return 扣减成功返回 true,数量不足返回 false
     */
    Boolean consume(Long lotId, BigDecimal consumedQty);

    /**
     * 批次追溯链(批次 -> 工单消耗 -> SN 绑定)
     *
     * @return { lot, consumptions, bindings }
     */
    Map<String, Object> trace(Long lotId);
}
