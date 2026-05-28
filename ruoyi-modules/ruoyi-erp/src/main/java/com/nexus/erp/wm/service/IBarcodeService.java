package com.nexus.erp.wm.service;

import com.nexus.erp.wm.barcode.BarcodeRule;

import java.util.Date;

/**
 * 条码服务
 *
 * P1-2 阶段先落地生成能力，后续再接数据库与扫描解析。
 */
public interface IBarcodeService {

    /**
     * 根据规则生成条码
     *
     * @param rule 规则
     * @param bizDate 业务日期
     * @return 条码值
     */
    String generate(BarcodeRule rule, Date bizDate);
}
