package com.nexus.erp.wm.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.domain.vo.WmBarcodeConfigVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 条码配置 Mapper
 */
public interface WmBarcodeConfigMapper extends BaseMapperPlus<WmBarcodeConfig, WmBarcodeConfigVo> {

    /**
     * 使用悲观锁查询条码配置行（SELECT FOR UPDATE）
     * <p>
     * 用于并发安全的序列号生成场景，确保同一事务内对该行加行锁，
     * 防止多个线程同时读取相同的序列号导致重复。
     * </p>
     *
     * @param configCode 条码配置编码
     * @return 条码配置对象，不存在时返回null
     */
    @Select("SELECT * FROM wm_barcode_config WHERE config_code = #{configCode} FOR UPDATE")
    WmBarcodeConfig selectForUpdate(@Param("configCode") String configCode);
}
