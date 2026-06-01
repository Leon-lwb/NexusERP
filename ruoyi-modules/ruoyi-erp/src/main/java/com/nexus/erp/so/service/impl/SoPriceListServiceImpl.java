package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoPriceList;
import com.nexus.erp.so.domain.SoPriceListLine;
import com.nexus.erp.so.domain.bo.SoPriceListBo;
import com.nexus.erp.so.domain.bo.SoPriceListLineBo;
import com.nexus.erp.so.domain.vo.SoPriceListLineVo;
import com.nexus.erp.so.domain.vo.SoPriceListVo;
import com.nexus.erp.so.mapper.SoPriceListLineMapper;
import com.nexus.erp.so.mapper.SoPriceListMapper;
import com.nexus.erp.so.service.ISoPriceListService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 销售价格表Service实现类
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoPriceListServiceImpl implements ISoPriceListService {

    private final SoPriceListMapper baseMapper;
    private final SoPriceListLineMapper soPriceListLineMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询销售价格表列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售价格表分页列表
     */
    @Override
    public TableDataInfo<SoPriceListVo> selectPageList(SoPriceListBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoPriceList> lqw = buildQueryWrapper(bo);
        Page<SoPriceListVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售价格表列表
     *
     * @param bo 查询参数
     * @return 销售价格表列表
     */
    @Override
    public List<SoPriceListVo> selectList(SoPriceListBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售价格表详情
     *
     * @param id 销售价格表ID
     * @return 销售价格表详情
     */
    @Override
    public SoPriceListVo selectById(Long id) {
        SoPriceListVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<SoPriceListLine> lineList = soPriceListLineMapper.selectList(
                new LambdaQueryWrapper<SoPriceListLine>()
                    .eq(SoPriceListLine::getPriceListId, id));
            List<SoPriceListLineVo> lineVoList = MapstructUtils.convert(lineList, SoPriceListLineVo.class);
            vo.setSoPriceListLineList(lineVoList);
        }
        return vo;
    }

    /**
     * 新增销售价格表
     *
     * @param bo 销售价格表业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoPriceListBo bo) {
        SoPriceList entity = MapstructUtils.convert(bo, SoPriceList.class);
        if (StringUtils.isBlank(entity.getListCode())) {
            entity.setListCode(autoCodeUtil.genSerialCode("PRICE_LIST_CODE", ""));
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus("ACTIVE");
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getPriceListId(), bo.getSoPriceListLineList());
        return rows;
    }

    /**
     * 修改销售价格表
     *
     * @param bo 销售价格表业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoPriceListBo bo) {
        SoPriceList entity = MapstructUtils.convert(bo, SoPriceList.class);
        soPriceListLineMapper.delete(new LambdaQueryWrapper<SoPriceListLine>()
            .eq(SoPriceListLine::getPriceListId, entity.getPriceListId()));
        insertOrUpdateLines(entity.getPriceListId(), bo.getSoPriceListLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售价格表
     *
     * @param ids 销售价格表ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        soPriceListLineMapper.delete(new LambdaQueryWrapper<SoPriceListLine>()
            .in(SoPriceListLine::getPriceListId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private void insertOrUpdateLines(Long priceListId, List<SoPriceListLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<SoPriceListLine> lineList = MapstructUtils.convert(lineBoList, SoPriceListLine.class);
            for (SoPriceListLine line : lineList) {
                line.setLineId(null);
                line.setPriceListId(priceListId);
            }
            soPriceListLineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<SoPriceList> buildQueryWrapper(SoPriceListBo bo) {
        LambdaQueryWrapper<SoPriceList> lqw = Wrappers.<SoPriceList>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getListCode()), SoPriceList::getListCode, bo.getListCode())
            .like(StringUtils.isNotBlank(bo.getListName()), SoPriceList::getListName, bo.getListName())
            .eq(StringUtils.isNotBlank(bo.getListType()), SoPriceList::getListType, bo.getListType())
            .eq(bo.getClientId() != null, SoPriceList::getClientId, bo.getClientId())
            .eq(StringUtils.isNotBlank(bo.getCurrency()), SoPriceList::getCurrency, bo.getCurrency())
            .eq(bo.getPriority() != null, SoPriceList::getPriority, bo.getPriority())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SoPriceList::getStatus, bo.getStatus());
        return lqw;
    }
}
