package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqSpareReturnBo;
import com.nexus.mes.eq.domain.vo.EqSpareReturnVo;

/**
 * MES备件退库单 Service 业务层接口（MES-EQ-04 收口）
 * <p>
 * 退库单确认时逐行调 {@link IEqSparePartService#stockTransaction}，{@code transactionType} 用
 * {@link IEqSparePartService#TXN_RETURN}（ERP 侧按入库方向过账），{@code refType = 'SPARE_RETURN'}、
 * {@code refId = returnId}；任一行失败整单回滚并保持 {@code DRAFT}。
 *
 * @author Nexus
 */
public interface IEqSpareReturnService {

    /** 状态: 草稿(可改可删可确认) */
    String STATUS_DRAFT = "DRAFT";
    /** 状态: 已确认(已过账, 终态) */
    String STATUS_CONFIRMED = "CONFIRMED";
    /** 状态: 已作废(终态) */
    String STATUS_CANCELLED = "CANCELLED";

    /** 退库类型: 维修退库 */
    String TYPE_REPAIR_RETURN = "REPAIR_RETURN";
    /** 退库类型: 余料退库 */
    String TYPE_EXCESS_RETURN = "EXCESS_RETURN";
    /** 退库类型: 其他 */
    String TYPE_OTHER = "OTHER";

    /** 库存流水关联单据类型(与 refId=returnId 配套) */
    String REF_TYPE = "SPARE_RETURN";

    /**
     * 分页查询退库单(不填充明细, 控制查询开销)
     *
     * @param bo        查询条件(returnNo/status/returnType/beginTime/endTime)
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<EqSpareReturnVo> selectPageList(EqSpareReturnBo bo, PageQuery pageQuery);

    /**
     * 退库单详情(含明细行)
     *
     * @param returnId 退库单ID
     * @return 详情；不存在返回 null
     */
    EqSpareReturnVo selectById(Long returnId);

    /**
     * 新增退库单草稿(含明细)
     * <p>明细数量必须 &gt; 0、备件必须存在；同一单内同一备件重复行<b>合并</b>（数量累加）；
     * 单号留空时由服务端生成。</p>
     *
     * @param bo 业务对象
     * @return 退库单ID
     */
    Long insertByBo(EqSpareReturnBo bo);

    /**
     * 修改退库单(仅 {@code DRAFT}；明细全量替换)
     *
     * @param bo 业务对象
     * @return 是否成功
     */
    Boolean updateByBo(EqSpareReturnBo bo);

    /**
     * 删除退库单(仅 {@code DRAFT}，明细一并逻辑删除)
     *
     * @param ids 退库单ID数组
     * @return 是否成功
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 确认退库(逐行 {@code TXN_RETURN} 过账)
     * <p>CAS（{@code DRAFT → CONFIRMED}）先占位再逐行过账，任一行失败整单回滚并保持 DRAFT；
     * 对已 {@code CONFIRMED} 的单据<b>幂等成功</b>（返回 true，不重复过账）。</p>
     *
     * @param returnId 退库单ID
     * @return 是否确认成功
     */
    Boolean confirm(Long returnId);
}
