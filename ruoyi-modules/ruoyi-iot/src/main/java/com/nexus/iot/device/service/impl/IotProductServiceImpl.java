package com.nexus.iot.device.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotProduct;
import com.nexus.iot.device.domain.bo.IotProductBo;
import com.nexus.iot.device.domain.vo.IotProductVo;
import com.nexus.iot.device.mapper.IotProductMapper;
import com.nexus.iot.device.service.IIotProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IoT产品Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotProductServiceImpl implements IIotProductService {

    private final IotProductMapper baseMapper;

    /**
     * 分页查询产品列表，按条件构建查询并转换为 VO 分页结果。
     *
     * @param bo        查询条件（编码/名称/类型/协议/状态等）
     * @param pageQuery 分页参数
     * @return 产品 VO 分页结果
     */
    @Override
    public TableDataInfo<IotProductVo> selectPageList(IotProductBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<IotProduct> lqw = buildQueryWrapper(bo);
        Page<IotProductVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询产品列表（不分页）。
     *
     * @param bo 查询条件
     * @return 产品 VO 列表
     */
    @Override
    public List<IotProductVo> selectList(IotProductBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询产品详情（VO）。
     *
     * @param id 产品 ID
     * @return 产品 VO，不存在返回 null
     */
    @Override
    public IotProductVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增产品。
     *
     * @param bo 产品业务对象
     * @return 插入影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotProductBo bo) {
        IotProduct entity = MapstructUtils.convert(bo, IotProduct.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改产品。
     *
     * @param bo 产品业务对象
     * @return 更新影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotProductBo bo) {
        IotProduct entity = MapstructUtils.convert(bo, IotProduct.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量逻辑删除产品。
     *
     * @param ids 产品 ID 数组
     * @return 删除影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<IotProduct> buildQueryWrapper(IotProductBo bo) {
        LambdaQueryWrapper<IotProduct> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getProductCode()), IotProduct::getProductCode, bo.getProductCode());
        lqw.like(StringUtils.isNotBlank(bo.getProductName()), IotProduct::getProductName, bo.getProductName());
        lqw.eq(StringUtils.isNotBlank(bo.getProductType()), IotProduct::getProductType, bo.getProductType());
        lqw.eq(StringUtils.isNotBlank(bo.getProtocolType()), IotProduct::getProtocolType, bo.getProtocolType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), IotProduct::getStatus, bo.getStatus());
        return lqw;
    }
}
