package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;

import java.util.List;

/**
 * 库存清单Service接口
 *
 * @author nexus
 * @date 2025-12-03
 */
public interface IWmMaterialStockService {

    /**
     * 分页查询库存清单列表
     *
     * @param bo         库存清单业务对象
     * @param pageQuery  分页参数
     * @return 库存清单分页集合
     */
    TableDataInfo<WmMaterialStockVo> selectPageList(WmMaterialStockBo bo, PageQuery pageQuery);

    /**
     * 查询库存清单列表
     *
     * @param bo 库存清单业务对象
     * @return 库存清单集合
     */
    List<WmMaterialStockVo> selectList(WmMaterialStockBo bo);

    /**
     * 查询库存清单
     *
     * @param id 库存清单主键
     * @return 库存清单
     */
    WmMaterialStockVo selectById(Long id);

    /**
     * 新增库存清单
     *
     * @param bo 库存清单业务对象
     * @return 结果
     */
    int insertByBo(WmMaterialStockBo bo);

    /**
     * 修改库存清单
     *
     * @param bo 库存清单业务对象
     * @return 结果
     */
    int updateByBo(WmMaterialStockBo bo);

    /**
     * 批量删除库存清单
     *
     * @param ids 需要删除的库存清单主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据分类ID列表查询库存清单
     *
     * @param categoryIds 分类ID列表
     * @return 库存清单集合
     */
    List<WmMaterialStockVo> selectByCategoryIds(List<Long> categoryIds);

    /**
     * IQC 不合格冻结:按收货单+物料定位批次,冻结对应库存行(frozen_flag=1)
     * <p>优先按收货行批次号定位; 无批次号时回退按批次ID定位; 两者都没有则记 warn 返回 0,
     * 由 {@code WmMaterialReceiveCrudServiceImpl.confirmShelf} 的单据级 iqc_status 门禁兜底拦截
     * (不再"定位不到批次就放行", 审计 MES-X-01)。</p>
     *
     * @param receiveId  收货单ID
     * @param materialId 物料ID
     * @return 冻结库存行数
     */
    int freezeStockByReceiveBatches(Long receiveId, Long materialId);

    /**
     * 解冻库存(按物料+批次号): 条件更新 frozen_flag 1 -&gt; 0(仅解冻当前冻结行, 幂等)。
     * <p>由 IQC 复检/让步后合格回写、批次放行等真实业务动作调用 —— 冻结必须可解,
     * 否则被 IQC 冻结的库存永久无法发料/出库(审计 MES-X-01)。</p>
     *
     * @param materialId 物料ID
     * @param batchCode  批次号
     * @param reason     解冻原因(记日志, 供审计追溯)
     * @return 解冻库存行数
     */
    int unfreezeByBatch(Long materialId, String batchCode, String reason);

    /**
     * 解冻库存(按收货单+物料): 定位该收货单该物料的批次号(无批次号时回退批次ID)后解冻对应库存行。
     *
     * @param receiveId  收货单ID
     * @param materialId 物料ID
     * @param reason     解冻原因
     * @return 解冻库存行数
     */
    int unfreezeByReceive(Long receiveId, Long materialId, String reason);
}
