package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdClient;
import com.nexus.erp.bd.mapper.BdClientMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final BdClientMapper bdClientMapper;
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
        fillClientNames(page.getRecords());
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
        List<SoPriceListVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillClientNames(list);
        return list;
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
            fillClientNames(List.of(vo));
        }
        return vo;
    }

    /**
     * 批量回填客户名称。
     * <p>{@code so_price_list} 只存 {@code client_id}（没有冗余名称列），列表与详情需要展示客户名称，
     * 因此按 clientId 批量查客户档案后回填；一次查询避免 N+1。</p>
     *
     * @param records 价格表记录（原地回填 clientName）
     */
    private void fillClientNames(List<SoPriceListVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> clientIds = records.stream()
            .map(SoPriceListVo::getClientId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (clientIds.isEmpty()) {
            return;
        }
        List<BdClient> clients = bdClientMapper.selectList(
            new LambdaQueryWrapper<BdClient>().in(BdClient::getClientId, clientIds));
        Map<Long, String> idToName = new HashMap<>();
        for (BdClient client : clients) {
            if (client.getClientId() != null) {
                idToName.put(client.getClientId(), client.getClientName());
            }
        }
        for (SoPriceListVo vo : records) {
            if (vo.getClientId() != null) {
                vo.setClientName(idToName.get(vo.getClientId()));
            }
        }
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
        // 明细为 null 表示「本次不提交明细」：保持库中原有明细不动；
        // 显式传空数组才是「清空全部明细」。原实现无条件「删行 + 重插」，
        // 传 null 会把该价格表的所有明细删光（与销售订单 T1.1 加固同源问题）。
        if (bo.getSoPriceListLineList() != null) {
            soPriceListLineMapper.delete(new LambdaQueryWrapper<SoPriceListLine>()
                .eq(SoPriceListLine::getPriceListId, entity.getPriceListId()));
            insertOrUpdateLines(entity.getPriceListId(), bo.getSoPriceListLineList());
        }
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

    /**
     * 批量新增/更新价格表明细行：清空行ID后按价格表ID归属并批量插入。
     *
     * @param priceListId 价格表ID
     * @param lineBoList  明细行业务对象列表
     */
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

    /**
     * 构建价格表分页/列表查询条件（按编码、名称、类型、客户、币种、优先级、状态、有效期区间动态拼接）。
     * <p>有效期区间语义：{@code validFrom} 作为「生效日期」下界、{@code validTo} 作为「失效日期」上界，
     * 即筛选有效期落在该区间内的价格表。</p>
     *
     * @param bo 查询参数
     * @return 查询条件构造器
     */
    private LambdaQueryWrapper<SoPriceList> buildQueryWrapper(SoPriceListBo bo) {
        LambdaQueryWrapper<SoPriceList> lqw = Wrappers.<SoPriceList>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getListCode()), SoPriceList::getListCode, bo.getListCode())
            .like(StringUtils.isNotBlank(bo.getListName()), SoPriceList::getListName, bo.getListName())
            .eq(StringUtils.isNotBlank(bo.getListType()), SoPriceList::getListType, bo.getListType())
            .eq(bo.getClientId() != null, SoPriceList::getClientId, bo.getClientId())
            .eq(StringUtils.isNotBlank(bo.getCurrency()), SoPriceList::getCurrency, bo.getCurrency())
            .eq(bo.getPriority() != null, SoPriceList::getPriority, bo.getPriority())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SoPriceList::getStatus, bo.getStatus())
            // 有效期区间过滤（原实现无此条件，前端传的日期范围被静默忽略）
            .ge(bo.getValidFrom() != null, SoPriceList::getValidFrom, bo.getValidFrom())
            .le(bo.getValidTo() != null, SoPriceList::getValidTo, bo.getValidTo());
        return lqw;
    }
}
