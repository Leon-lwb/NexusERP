package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmStockAlertBo;
import org.dromara.erp.wm.domain.vo.WmStockAlertVo;

import java.util.List;

/**
 * 安全库存预警Service接口
 *
 * @author nexus
 */
public interface IWmStockAlertService {

    /**
     * 分页查询安全库存预警列表
     */
    TableDataInfo<WmStockAlertVo> selectPageList(WmStockAlertBo bo, PageQuery pageQuery);

    /**
     * 查询安全库存预警列表
     */
    List<WmStockAlertVo> selectList(WmStockAlertBo bo);

    /**
     * 查询安全库存预警详情
     */
    WmStockAlertVo selectById(Long id);

    /**
     * 新增安全库存预警
     */
    int insertByBo(WmStockAlertBo bo);

    /**
     * 修改安全库存预警
     */
    int updateByBo(WmStockAlertBo bo);

    /**
     * 删除安全库存预警
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行安全库存预警检查
     * 扫描所有设置了安全库存/最低库存的物料，对比当前库存，生成预警记录
     */
    void checkStockAlerts();

    /**
     * 处理预警记录
     *
     * @param alertId 预警ID
     * @param handledRemark 处理说明
     */
    void handleAlert(Long alertId, String handledRemark);
}
