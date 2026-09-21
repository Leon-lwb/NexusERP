package com.nexus.erp.wm.barcode;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.mapper.WmBarcodeConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.dynamic.datasource.annotation.DS;

/**
 * 基于数据库的序列号提供者
 *
 * <p>通过 wm_barcode_config 表维护每种配置的当前序列号，
 * 使用数据库悲观锁（SELECT FOR UPDATE）保证并发安全。</p>
 *
 * <p>并发安全机制：{@code @Transactional} + {@code SELECT FOR UPDATE} 行锁，
 * 确保同一时刻只有一个线程可以读取和更新序列号，避免重复。</p>
 *
 * @author wanghuan
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class DatabaseSequenceProvider implements BarcodeSequenceProvider {

    private final WmBarcodeConfigMapper barcodeConfigMapper;

    /**
     * 获取下一个序列号（并发安全）
     *
     * <p>使用 SELECT FOR UPDATE 悲观锁锁定配置行，确保并发场景下序列号唯一。
     * 调用方需确保在事务上下文中执行。</p>
     *
     * @param configCode 条码配置编码
     * @return 新的序列号（自增后的值）
     * @throws ServiceException 当配置编码不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long nextSequence(String configCode) {
        // 使用悲观锁获取配置行，防止并发读取相同序列号
        WmBarcodeConfig config = barcodeConfigMapper.selectForUpdate(configCode);

        if (config == null) {
            throw new ServiceException("条码配置不存在: " + configCode);
        }

        // 自增序列并返回新值
        Long currentSeq = config.getCurrentSequence() == null ? 0L : config.getCurrentSequence();
        long nextSeq = currentSeq + 1;
        config.setCurrentSequence(nextSeq);
        barcodeConfigMapper.updateById(config);

        log.debug("条码序列号自增: configCode={}, currentSeq={}, nextSeq={}", configCode, currentSeq, nextSeq);
        return nextSeq;
    }
}
