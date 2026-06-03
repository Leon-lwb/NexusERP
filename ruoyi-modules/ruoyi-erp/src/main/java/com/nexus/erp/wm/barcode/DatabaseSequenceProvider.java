package com.nexus.erp.wm.barcode;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.mapper.WmBarcodeConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于数据库的序列号提供者
 *
 * 通过 wm_barcode_config 表维护每种配置的当前序列号，
 * 使用数据库行锁保证并发安全。
 */
@RequiredArgsConstructor
@Component
public class DatabaseSequenceProvider implements BarcodeSequenceProvider {

    private final WmBarcodeConfigMapper barcodeConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long nextSequence(String configCode) {
        WmBarcodeConfig config = barcodeConfigMapper.selectOne(
            Wrappers.<WmBarcodeConfig>lambdaQuery()
                .eq(WmBarcodeConfig::getConfigCode, configCode));

        if (config == null) {
            throw new RuntimeException("条码配置不存在: " + configCode);
        }

        // 自增序列并返回新值
        Long currentSeq = config.getCurrentSequence() == null ? 0L : config.getCurrentSequence();
        long nextSeq = currentSeq + 1;
        config.setCurrentSequence(nextSeq);
        barcodeConfigMapper.updateById(config);

        return nextSeq;
    }
}
