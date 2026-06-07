package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 物料入库单Service接口
 * <p>
 * Q-003 重构后：本接口保留为兼容层，实际实现委托给 CrudService + ExecutorService。
 * writeBackPurchaseOrderReceivedQty 已移除（由 PuPurchaseReceiveWritebackService 承担）。
 *
 * @author nexus
 */
public interface IWmMaterialReceiveService extends WfCallBackServiceI {

    TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery);

    List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo);

    WmMaterialReceiveVo selectById(Long id);

    int insertByBo(WmMaterialReceiveBo bo);

    int updateByBo(WmMaterialReceiveBo bo);

    int deleteByIds(Long[] ids);

    boolean updateWithValidation(WmMaterialReceiveBo bo);

    boolean submit(String receiveIdStr, String selectFlowId);

    boolean executeReceive(Long receiveId);

    boolean confirmShelf(Long receiveId);

    String getProcInsId(String receiveIdStr);
}
