package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.barcode.BarcodeGenerator;
import com.nexus.erp.wm.barcode.BarcodeRule;
import com.nexus.erp.wm.barcode.BarcodeSequenceProvider;
import com.nexus.erp.wm.domain.WmBarcode;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.domain.bo.WmBarcodeBo;
import com.nexus.erp.wm.domain.vo.WmBarcodeVo;
import com.nexus.erp.wm.mapper.WmBarcodeConfigMapper;
import com.nexus.erp.wm.mapper.WmBarcodeMapper;
import com.nexus.erp.wm.service.IWmBarcodeService;
import com.nexus.erp.wm.trace.WmBarcodeIdentityGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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

    /**
     * 基础数据访问映射器（Mapper）
     */
    private final WmBarcodeMapper baseMapper;

    /**
     * 条码配置数据访问映射器（Mapper）
     */
    private final WmBarcodeConfigMapper barcodeConfigMapper;

    /**
     * 条码生成器
     */
    private final BarcodeGenerator barcodeGenerator;

    /**
     * 条码序列提供器
     */
    private final BarcodeSequenceProvider barcodeSequenceProvider;

    private final WmBarcodeIdentityGuard identities;

    /**
     * 分页查询条码列表
     *
     * @param bo 条码业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmBarcodeVo> selectPageList(WmBarcodeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmBarcode> lqw = buildQueryWrapper(bo);
        Page<WmBarcodeVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询条码列表
     *
     * @param bo 条码业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmBarcodeVo> selectList(WmBarcodeBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询条码
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmBarcodeVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增条码
     *
     * @param bo 条码业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmBarcodeBo bo) {
        WmBarcode entity = MapstructUtils.convert(bo, WmBarcode.class);
        identities.prepare(entity, false);
        return baseMapper.insert(entity);
    }

    /**
     * 修改条码
     *
     * @param bo 条码业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmBarcodeBo bo) {
        WmBarcode requested = MapstructUtils.convert(bo, WmBarcode.class);
        identities.assertImmutable(identities.lock(bo.getBarcodeId()), requested);
        WmBarcode entity = new WmBarcode();
        entity.setBarcodeId(bo.getBarcodeId());
        entity.setStatus(bo.getStatus());
        entity.setExpireTime(bo.getExpireTime());
        entity.setRemark(bo.getRemark());
        return baseMapper.updateById(entity);
    }

    /**
     * 删除条码
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        int count = 0;
        for (Long id : Arrays.stream(ids).distinct().sorted().toList()) {
            identities.lock(id);
            WmBarcode disabled = new WmBarcode();
            disabled.setBarcodeId(id);
            disabled.setStatus("INACTIVE");
            count += baseMapper.updateById(disabled);
        }
        return count;
    }

    /**
     * 生成条码
     *
     * @param configId 配置 ID
     * @param bizType 参数 bizType
     * @param bizId biz ID
     * @param bizLineId biz行 ID
     * @param materialId 物料 ID
     * @param batchId 批次 ID
     * @param stockId 库存 ID
     * @param bizDate 业务日期
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateAndSave(
            Long configId,
            String bizType,
            Long bizId,
            Long bizLineId,
            Long materialId,
            Long batchId,
            Long stockId,
            Long snId,
            Date bizDate) {
        WmBarcodeConfig config = configId == null ? null : barcodeConfigMapper.selectById(configId);
        return generateAndSaveInternal(
                config,
                null,
                bizType,
                bizId,
                bizLineId,
                materialId,
                batchId,
                stockId,
                snId,
                bizDate);
    }

    /**
     * 生成条码
     *
     * @param configCode 配置 编码
     * @param bizType 参数 bizType
     * @param bizId biz ID
     * @param bizLineId biz行 ID
     * @param materialId 物料 ID
     * @param batchId 批次 ID
     * @param stockId 库存 ID
     * @param bizDate 业务日期
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateAndSaveByCode(
            String configCode,
            String bizType,
            Long bizId,
            Long bizLineId,
            Long materialId,
            Long batchId,
            Long stockId,
            Long snId,
            Date bizDate) {
        WmBarcodeConfig config =
                StringUtils.isBlank(configCode)
                        ? null
                        : barcodeConfigMapper.selectOne(
                                Wrappers.<WmBarcodeConfig>lambdaQuery()
                                        .eq(WmBarcodeConfig::getConfigCode, configCode));
        return generateAndSaveInternal(
                config,
                configCode,
                bizType,
                bizId,
                bizLineId,
                materialId,
                batchId,
                stockId,
                snId,
                bizDate);
    }

    /**
     * 条码生成与落库的统一处理逻辑。
     *
     * 说明：
     * - 先尝试使用指定配置；若不存在则回退到系统默认配置
     * - 再把数据库配置映射成轻量规则对象
     * - 最后生成条码并保存实例记录
     */
    private String generateAndSaveInternal(
            WmBarcodeConfig config,
            String configCode,
            String bizType,
            Long bizId,
            Long bizLineId,
            Long materialId,
            Long batchId,
            Long stockId,
            Long snId,
            Date bizDate) {
        WmBarcodeConfig effectiveConfig = resolveEffectiveConfig(config, configCode);
        if (effectiveConfig == null) {
            throw new RuntimeException("条码配置不存在，且未找到可用默认配置");
        }

        WmBarcode barcode = new WmBarcode();
        barcode.setConfigId(effectiveConfig.getConfigId());
        barcode.setConfigCode(effectiveConfig.getConfigCode());
        barcode.setBizType(bizType);
        barcode.setBizId(bizId);
        barcode.setBizLineId(bizLineId);
        barcode.setMaterialId(materialId);
        barcode.setBatchId(batchId);
        barcode.setStockId(stockId);
        barcode.setSnId(snId);
        barcode.setStatus("ACTIVE");
        barcode.setGenerateTime(bizDate == null ? new Date() : bizDate);
        barcode.setRemark("系统生成条码");
        identities.lockBinding(barcode, true);
        BarcodeRule rule = buildRule(effectiveConfig);
        long sequence = barcodeSequenceProvider.nextSequence(effectiveConfig.getConfigCode());
        String barcodeCode = barcodeGenerator.generate(rule, bizDate, sequence);
        barcode.setBarcodeCode(barcodeCode);
        identities.finishRegistration(barcode);
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
        // 1. 优先使用外部传入且有效的配置
        if (config != null && (config.getEnableFlag() == null || config.getEnableFlag() != 0L)) {
            return config;
        }

        // 2. 尝试按指定编码查找启用版本
        if (StringUtils.isNotBlank(configCode)) {
            WmBarcodeConfig configByCode =
                    barcodeConfigMapper.selectOne(
                            Wrappers.<WmBarcodeConfig>lambdaQuery()
                                    .eq(WmBarcodeConfig::getConfigCode, configCode)
                                    .eq(WmBarcodeConfig::getEnableFlag, 1L));
            if (configByCode != null) {
                return configByCode;
            }
        }

        // 3. 回退到默认配置
        WmBarcodeConfig defaultConfig =
                barcodeConfigMapper.selectOne(
                        Wrappers.<WmBarcodeConfig>lambdaQuery()
                                .eq(WmBarcodeConfig::getConfigCode, "WM_RECEIVE_DEFAULT")
                                .eq(WmBarcodeConfig::getEnableFlag, 1L));
        if (defaultConfig != null) {
            return defaultConfig;
        }

        // 4. 最后兜底取第一条启用配置
        return barcodeConfigMapper.selectOne(
                Wrappers.<WmBarcodeConfig>lambdaQuery()
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
        rule.setDatePattern(
                StringUtils.isNotBlank(config.getDatePattern())
                        ? config.getDatePattern()
                        : "yyyyMMdd");
        rule.setSequenceLength(config.getSequenceLength() == null ? 6 : config.getSequenceLength());
        rule.setWithCheckDigit(
                config.getWithCheckDigit() != null && config.getWithCheckDigit() == 1L);
        return rule;
    }

    private LambdaQueryWrapper<WmBarcode> buildQueryWrapper(WmBarcodeBo bo) {
        LambdaQueryWrapper<WmBarcode> lqw = Wrappers.lambdaQuery();
        lqw.eq(
                StringUtils.isNotBlank(bo.getBarcodeCode()),
                WmBarcode::getBarcodeCode,
                bo.getBarcodeCode());
        lqw.eq(
                StringUtils.isNotBlank(bo.getPrimaryObjectType()),
                WmBarcode::getPrimaryObjectType,
                bo.getPrimaryObjectType());
        lqw.eq(bo.getConfigId() != null, WmBarcode::getConfigId, bo.getConfigId());
        lqw.eq(StringUtils.isNotBlank(bo.getBizType()), WmBarcode::getBizType, bo.getBizType());
        lqw.eq(bo.getBizId() != null, WmBarcode::getBizId, bo.getBizId());
        lqw.eq(bo.getMaterialId() != null, WmBarcode::getMaterialId, bo.getMaterialId());
        lqw.eq(bo.getStockId() != null, WmBarcode::getStockId, bo.getStockId());
        lqw.eq(bo.getBatchId() != null, WmBarcode::getBatchId, bo.getBatchId());
        lqw.eq(bo.getSnId() != null, WmBarcode::getSnId, bo.getSnId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmBarcode::getStatus, bo.getStatus());
        return lqw;
    }
}
