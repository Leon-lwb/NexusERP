package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 物料入库单Service接口
 *
 * @author ruoyi
 */
public interface IWmMaterialReceiveService extends WfCallBackServiceI {

    TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery);

    List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo);

    WmMaterialReceiveVo selectById(Long id);

    int insertByBo(WmMaterialReceiveBo bo);

    int updateByBo(WmMaterialReceiveBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 更新物料入库单并执行必要校验
     */
    boolean updateWithValidation(WmMaterialReceiveBo bo);

    /**
     * 提交物料入库单并启动流程
     */
    boolean submit(String receiveIdStr, String selectFlowId);

    /**
     * 执行物料入库
     */
    boolean executeReceive(Long receiveId);

    /**
     * 获取流程实例ID
     */
    String getProcInsId(String receiveIdStr);

    /**
     * 回写采购订单行已收货数量
     */
    void writeBackPurchaseOrderReceivedQty(Long receiveId);
}
