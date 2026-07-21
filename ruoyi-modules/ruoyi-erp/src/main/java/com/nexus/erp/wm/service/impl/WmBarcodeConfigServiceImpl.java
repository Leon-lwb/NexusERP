package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.domain.bo.WmBarcodeConfigBo;
import com.nexus.erp.wm.domain.vo.WmBarcodeConfigVo;
import com.nexus.erp.wm.mapper.WmBarcodeConfigMapper;
import com.nexus.erp.wm.service.IWmBarcodeConfigService;
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

    /** 基础数据访问映射器（Mapper） */
    private final WmBarcodeConfigMapper baseMapper;

    /**
     * 分页查询条码配置列表
     *
     * @param bo 条码配置业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
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

    /**
     * 查询条码配置列表
     *
     * @param bo 条码配置业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmBarcodeConfigVo> selectList(WmBarcodeConfigBo bo) {
        return baseMapper.selectList(buildQueryWrapper(bo)).stream().map(this::toVo).collect(Collectors.toList());
    }

    /**
     * 查询条码配置
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmBarcodeConfigVo selectById(Long id) {
        WmBarcodeConfig entity = baseMapper.selectById(id);
        return entity == null ? null : toVo(entity);
    }

    /**
     * 新增条码配置
     *
     * @param bo 条码配置业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmBarcodeConfigBo bo) {
        // 校验 configCode 唯一性
        long count = baseMapper.selectCount(Wrappers.<WmBarcodeConfig>lambdaQuery()
            .eq(WmBarcodeConfig::getConfigCode, bo.getConfigCode()));
        if (count > 0) {
            throw new RuntimeException("配置编码已存在: " + bo.getConfigCode());
        }
        WmBarcodeConfig entity = MapstructUtils.convert(bo, WmBarcodeConfig.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改条码配置
     *
     * @param bo 条码配置业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmBarcodeConfigBo bo) {
        WmBarcodeConfig entity = MapstructUtils.convert(bo, WmBarcodeConfig.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 删除条码配置
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
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
        vo.setCurrentSequence(entity.getCurrentSequence());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
