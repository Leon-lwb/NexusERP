package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmBarcodeBo;
import org.dromara.erp.wm.domain.vo.WmBarcodeVo;

import java.util.Date;
import java.util.List;

/**
 * 条码实例 Service 接口
 */
public interface IWmBarcodeService {

    TableDataInfo<WmBarcodeVo> selectPageList(WmBarcodeBo bo, PageQuery pageQuery);

    List<WmBarcodeVo> selectList(WmBarcodeBo bo);

    WmBarcodeVo selectById(Long id);

    int insertByBo(WmBarcodeBo bo);

    int updateByBo(WmBarcodeBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 生成条码并保存为实例记录
     *
     * @return 生成后的条码值
     */
    String generateAndSave(Long configId, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Date bizDate);

    /**
     * 按配置编码生成条码并保存为实例记录
     *
     * @return 生成后的条码值
     */
    String generateAndSaveByCode(String configCode, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Date bizDate);
}
