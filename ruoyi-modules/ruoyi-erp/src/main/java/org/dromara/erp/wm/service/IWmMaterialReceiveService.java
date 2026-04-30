package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveVo;

import java.util.List;

/**
 * 物料入库单Service接口
 *
 * @author ruoyi
 */
public interface IWmMaterialReceiveService {

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
}
