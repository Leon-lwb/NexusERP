package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdClient;
import com.nexus.erp.bd.domain.bo.BdClientBo;
import com.nexus.erp.bd.domain.vo.BdClientVo;
import com.nexus.erp.bd.mapper.BdClientMapper;
import com.nexus.erp.bd.service.IBdClientService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 客户信息Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdClientServiceImpl implements IBdClientService {

    private final BdClientMapper baseMapper;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<BdClientVo> selectPageList(BdClientBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdClient> lqw = buildQueryWrapper(bo);
        Page<BdClientVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdClientVo> selectList(BdClientBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdClientVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdClientBo bo) {
        BdClient entity = MapstructUtils.convert(bo, BdClient.class);
        if (StringUtils.isBlank(entity.getClientCode())) {
            entity.setClientCode(autoCodeUtil.genSerialCode("CLIENT_CODE", null));
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdClientBo bo) {
        BdClient entity = MapstructUtils.convert(bo, BdClient.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdClient> buildQueryWrapper(BdClientBo bo) {
        LambdaQueryWrapper<BdClient> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getClientCode()), BdClient::getClientCode, bo.getClientCode());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), BdClient::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getClientType()), BdClient::getClientType, bo.getClientType());
        lqw.like(StringUtils.isNotBlank(bo.getTel()), BdClient::getTel, bo.getTel());
        lqw.eq(bo.getDeptId() != null, BdClient::getDeptId, bo.getDeptId());
        return lqw;
    }
}
