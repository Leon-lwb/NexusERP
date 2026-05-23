package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmBarcodeConfig;
import org.dromara.erp.wm.domain.bo.WmBarcodeConfigBo;
import org.dromara.erp.wm.domain.vo.WmBarcodeConfigVo;
import org.dromara.erp.wm.mapper.WmBarcodeConfigMapper;
import org.dromara.erp.wm.service.IWmBarcodeConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 条码配置 Service 实现
 *
 * 说明：
 * - 条码实例生成前，先从这里读取配置
 * - 该服务是条码规则引擎的入口之一
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmBarcodeConfigServiceImpl implements IWmBarcodeConfigService {

    private final WmBarcodeConfigMapper baseMapper;

    @Override
    public TableDataInfo<WmBarcodeConfigVo> selectPageList(WmBarcodeConfigBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmBarcodeConfig> lqw = buildQueryWrapper(bo);
        Page<WmBarcodeConfig> page = baseMapper.selectPage(pageQuery.build(), lqw);
        List<WmBarcodeConfigVo> records = page.getRecords().stream()
            .map(this::toVo)
            .collect(Collectors.toList());
        Page<WmBarcodeConfigVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return TableDataInfo.build(voPage);
    }

    @Override
    public List<WmBarcodeConfigVo> selectList(WmBarcodeConfigBo bo) {
        return baseMapper.selectList(buildQueryWrapper(bo)).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public WmBarcodeConfigVo selectById(Long id) {
        WmBarcodeConfig entity = baseMapper.selectById(id);
        return entity == null ? null : toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmBarcodeConfigBo bo) {
        WmBarcodeConfig entity = MapstructUtils.convert(bo, WmBarcodeConfig.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmBarcodeConfigBo bo) {
        WmBarcodeConfig entity = MapstructUtils.convert(bo, WmBarcodeConfig.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmBarcodeConfig> buildQueryWrapper(WmBarcodeConfigBo bo) {
        LambdaQueryWrapper<WmBarcodeConfig> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getConfigCode()), WmBarcodeConfig::getConfigCode, bo.getConfigCode());
        lqw.like(StringUtils.isNotBlank(bo.getConfigName()), WmBarcodeConfig::getConfigName, bo.getConfigName());
        lqw.eq(StringUtils.isNotBlank(bo.getBizType()), WmBarcodeConfig::getBizType, bo.getBizType());
        lqw.eq(bo.getCategoryId() != null, WmBarcodeConfig::getCategoryId, bo.getCategoryId());
        lqw.eq(bo.getEnableFlag() != null, WmBarcodeConfig::getEnableFlag, bo.getEnableFlag());
        return lqw;
    }

    /**
     * 手动映射，绕开自动转换器对该 VO 的注册依赖。
     */
    private WmBarcodeConfigVo toVo(WmBarcodeConfig entity) {
        WmBarcodeConfigVo vo = new WmBarcodeConfigVo();
        vo.setConfigId(entity.getConfigId());
        vo.setConfigCode(entity.getConfigCode());
        vo.setConfigName(entity.getConfigName());
        vo.setPrefix(entity.getPrefix());
        vo.setDatePattern(entity.getDatePattern());
        vo.setSequenceLength(entity.getSequenceLength());
        vo.setWithCheckDigit(entity.getWithCheckDigit());
        vo.setBizType(entity.getBizType());
        vo.setCategoryId(entity.getCategoryId());
        vo.setEnableFlag(entity.getEnableFlag());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
