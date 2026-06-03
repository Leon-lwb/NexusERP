package com.nexus.erp.wm.barcode;

/**
 * 条码序列号提供者接口
 *
 * 实现方式可选：
 * - 数据库序列（推荐，通过 wm_barcode_config 表的 sequence 字段）
 * - Redis INCR
 * - 雪花算法
 */
@FunctionalInterface
public interface BarcodeSequenceProvider {

    /**
     * 获取下一个序列号
     *
     * @param configCode 配置编码（用于区分不同序列）
     * @return 序列号（>= 1）
     */
    long nextSequence(String configCode);
}
