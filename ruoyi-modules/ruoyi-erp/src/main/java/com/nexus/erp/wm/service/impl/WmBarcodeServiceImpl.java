package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.barcode.BarcodeGenerator;
import com.nexus.erp.wm.barcode.BarcodeRule;
import com.nexus.erp.wm.domain.WmBarcode;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.domain.bo.WmBarcodeBo;
import com.nexus.erp.wm.domain.vo.WmBarcodeVo;
import com.nexus.erp.wm.mapper.WmBarcodeConfigMapper;
import com.nexus.erp.wm.mapper.WmBarcodeMapper;
import com.nexus.erp.wm.service.IWmBarcodeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.Date;
import java.util.List;

/**
 * 条码实例 Service 实现
 *
 * 说明：
 * - 负责条码实例落库、查询和生成
 * - 生成逻辑先复用轻量规则对象，后续可平滑迁移到数据库规则配置
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmBarcodeServiceImpl implements IWmBarcodeService {

    private final WmBarcodeMapper baseMapper;
    private final WmBarcodeConfigMapper barcodeConfigMapper;
    private final BarcodeGenerator barcodeGenerator = new BarcodeGenerator();

    @Override
    public TableDataInfo<WmBarcodeVo> selectPageList(WmBarcodeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmBarcode> lqw = buildQueryWrapper(bo);
        Page<WmBarcodeVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmBarcodeVo> selectList(WmBarcodeBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmBarcodeVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmBarcodeBo bo) {
        WmBarcode entity = MapstructUtils.convert(bo, WmBarcode.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmBarcodeBo bo) {
        WmBarcode entity = MapstructUtils.convert(bo, WmBarcode.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateAndSave(Long configId, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Date bizDate) {
        WmBarcodeConfig config = configId == null ? null : barcodeConfigMapper.selectById(configId);
        return generateAndSaveInternal(config, null, bizType, bizId, bizLineId, materialId, batchId, stockId, bizDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateAndSaveByCode(String configCode, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Date bizDate) {
        WmBarcodeConfig config = StringUtils.isBlank(configCode) ? null : barcodeConfigMapper.selectOne(Wrappers.<WmBarcodeConfig>lambdaQuery()
            .eq(WmBarcodeConfig::getConfigCode, configCode));
        return generateAndSaveInternal(config, configCode, bizType, bizId, bizLineId, materialId, batchId, stockId, bizDate);
    }

    /**
     * 条码生成与落库的统一处理逻辑。
     *
     * 说明：
     * - 先尝试使用指定配置；若不存在则回退到系统默认配置
     * - 再把数据库配置映射成轻量规则对象
     * - 最后生成条码并保存实例记录
     */
    private String generateAndSaveInternal(WmBarcodeConfig config, String configCode, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Date bizDate) {
        WmBarcodeConfig effectiveConfig = resolveEffectiveConfig(config, configCode);
        if (effectiveConfig == null) {
            throw new RuntimeException("条码配置不存在，且未找到可用默认配置");
        }

        BarcodeRule rule = buildRule(effectiveConfig);
        String barcodeCode = barcodeGenerator.generate(rule, bizDate);

        WmBarcode barcode = new WmBarcode();
        barcode.setBarcodeCode(barcodeCode);
        barcode.setConfigId(effectiveConfig.getConfigId());
        barcode.setConfigCode(effectiveConfig.getConfigCode());
        barcode.setBizType(bizType);
        barcode.setBizId(bizId);
        barcode.setBizLineId(bizLineId);
        barcode.setMaterialId(materialId);
        barcode.setBatchId(batchId);
        barcode.setStockId(stockId);
        barcode.setStatus("ACTIVE");
        barcode.setGenerateTime(bizDate == null ? new Date() : bizDate);
        barcode.setRemark("系统生成条码");
        baseMapper.insert(barcode);
        return barcodeCode;
    }

    /**
     * 解析实际使用的条码配置。
     *
     * 规则：
     * 1. 优先使用外部传入且有效的配置
     * 2. 若配置失效或不存在，则回退到系统默认编码 WM_RECEIVE_DEFAULT
     * 3. 再不行则使用第一条启用中的配置兜底
     */
    private WmBarcodeConfig resolveEffectiveConfig(WmBarcodeConfig config, String configCode) {
        if (config != null && (config.getEnableFlag() == null || config.getEnableFlag() != 0L)) {
            return config;
        }

        WmBarcodeConfig defaultConfig = barcodeConfigMapper.selectOne(Wrappers.<WmBarcodeConfig>lambdaQuery()
            .eq(WmBarcodeConfig::getConfigCode, "WM_RECEIVE_DEFAULT")
            .eq(WmBarcodeConfig::getEnableFlag, 1L));
        if (defaultConfig != null) {
            return defaultConfig;
        }

        if (StringUtils.isNotBlank(configCode)) {
            WmBarcodeConfig configByCode = barcodeConfigMapper.selectOne(Wrappers.<WmBarcodeConfig>lambdaQuery()
                .eq(WmBarcodeConfig::getConfigCode, configCode)
                .eq(WmBarcodeConfig::getEnableFlag, 1L));
            if (configByCode != null) {
                return configByCode;
            }
        }

        return barcodeConfigMapper.selectOne(Wrappers.<WmBarcodeConfig>lambdaQuery()
            .eq(WmBarcodeConfig::getEnableFlag, 1L)
            .last("limit 1"));
    }

    /**
     * 将数据库条码配置转换为轻量规则对象。
     */
    private BarcodeRule buildRule(WmBarcodeConfig config) {
        BarcodeRule rule = new BarcodeRule();
        rule.setRuleCode(config.getConfigCode());
        rule.setPrefix(config.getPrefix());
        rule.setDatePattern(StringUtils.isNotBlank(config.getDatePattern()) ? config.getDatePattern() : "yyyyMMdd");
        rule.setSequenceLength(config.getSequenceLength() == null ? 6 : config.getSequenceLength());
        rule.setWithCheckDigit(config.getWithCheckDigit() != null && config.getWithCheckDigit() == 1L);
        return rule;
    }

    private LambdaQueryWrapper<WmBarcode> buildQueryWrapper(WmBarcodeBo bo) {
        LambdaQueryWrapper<WmBarcode> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getBarcodeCode()), WmBarcode::getBarcodeCode, bo.getBarcodeCode());
        lqw.eq(bo.getConfigId() != null, WmBarcode::getConfigId, bo.getConfigId());
        lqw.eq(StringUtils.isNotBlank(bo.getBizType()), WmBarcode::getBizType, bo.getBizType());
        lqw.eq(bo.getBizId() != null, WmBarcode::getBizId, bo.getBizId());
        lqw.eq(bo.getMaterialId() != null, WmBarcode::getMaterialId, bo.getMaterialId());
        lqw.eq(bo.getStockId() != null, WmBarcode::getStockId, bo.getStockId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmBarcode::getStatus, bo.getStatus());
        return lqw;
    }
}
