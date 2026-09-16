package com.nexus.common.sign.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.domain.bo.SealQueryBo;
import com.nexus.common.sign.domain.vo.AvailableSealVo;
import com.nexus.common.sign.domain.vo.SealVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.service.IESSealService;
import com.nexus.common.sign.support.SealAvailabilityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 印章服务实现。
 *
 * <p>印章可用性的<b>授权判定</b>统一委托 {@link SealAvailabilityResolver}（纯逻辑），
 * 本类只负责数据装载与印章自身约束（类型/名称唯一/日期窗口/状态机）。</p>
 *
 * <p><b>数据源（类级 {@code @DS("master")}，勿删）：</b>es_seal / es_seal_grant 只存在于
 * ry-vue 库（主数据源 master）。本模块不继承调用方的数据源上下文，否则 MES/ERP 上下文下
 * 会打到 ry-mes / ry-erp 库报「表不存在」。理由与注解层级选择详见
 * {@link ESignatureServiceImpl} 的类注释。</p>
 *
 * <p><b>同类自调用不触发 AOP：</b>{@code buildQueryWrapper} / {@code loadGrants} /
 * {@code toAvailableSealVo} / {@code validateSealBo} 均为 private 辅助方法，由公开方法
 * 内部调用；数据源键由公开方法的类级注解压栈，故无需也不应在这些辅助方法上重复标注。</p>
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
@DS("master")
public class EsSealServiceImpl extends ServiceImpl<EsSealMapper, EsSeal> implements IESSealService {

    private final EsSealGrantMapper sealGrantMapper;

    @Override
    public TableDataInfo<SealVo> selectPageList(SealQueryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EsSeal> lqw = buildQueryWrapper(bo);
        Page<SealVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SealVo> selectList(SealQueryBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SealVo selectById(Long sealId) {
        return baseMapper.selectVoById(sealId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(SealBo bo) {
        validateSealBo(bo, null);
        EsSeal entity = MapstructUtils.convert(bo, EsSeal.class);
        // 新建印章一律为草稿态；启用/停用由 changeStatus 控制
        entity.setStatus(SealStatus.DRAFT);
        entity.setSealVersion(1);
        baseMapper.insert(entity);
        return entity.getSealId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SealBo bo) {
        EsSeal existing = baseMapper.selectById(bo.getSealId());
        if (existing == null) {
            throw new ServiceException("印章不存在");
        }
        if (SealStatus.DESTROYED.equals(existing.getStatus())) {
            throw new ServiceException("印章已销毁，不可修改");
        }
        // 印章类型不可变更：updateById 跳过 null，若允许 PERSONAL ↔ COMPANY 互转会残留
        // ownerUserId（公司章带归属人），故类型在设计上不可变，后端强制校验（前端已禁用该字段）
        if (!Objects.equals(existing.getSealType(), bo.getSealType())) {
            throw new ServiceException("印章类型不可变更，请新建印章");
        }
        validateSealBo(bo, existing);
        EsSeal entity = MapstructUtils.convert(bo, EsSeal.class);
        // 章面（原图）更换 -> 版本号自增，签名记录可据此判断用章版本
        if (bo.getSealOssId() != null && !bo.getSealOssId().equals(existing.getSealOssId())) {
            int current = existing.getSealVersion() == null ? 0 : existing.getSealVersion();
            entity.setSealVersion(current + 1);
        }
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(List<Long> sealIds) {
        if (sealIds == null || sealIds.isEmpty()) {
            return 0;
        }
        List<EsSeal> seals = baseMapper.selectByIds(sealIds);
        for (EsSeal seal : seals) {
            if (SealStatus.ENABLED.equals(seal.getStatus())) {
                throw new ServiceException("印章【{}】处于启用状态，请先停用", seal.getSealName());
            }
        }
        return baseMapper.deleteByIds(sealIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(Long sealId, String status) {
        EsSeal existing = baseMapper.selectById(sealId);
        if (existing == null) {
            throw new ServiceException("印章不存在");
        }
        if (SealStatus.DESTROYED.equals(existing.getStatus())) {
            throw new ServiceException("印章已销毁，状态不可再变更");
        }
        EsSeal entity = new EsSeal();
        entity.setSealId(sealId);
        entity.setStatus(status);
        return baseMapper.updateById(entity);
    }

    @Override
    public List<AvailableSealVo> listAvailableSeals(String bizType) {
        Long userId = LoginHelper.getUserId();
        Long deptId = LoginHelper.getDeptId();
        List<AvailableSealVo> result = new ArrayList<>();
        // 个人章：仅本人启用的
        List<EsSeal> personalSeals = baseMapper.selectList(Wrappers.<EsSeal>lambdaQuery()
            .eq(EsSeal::getSealType, SealType.PERSONAL)
            .eq(EsSeal::getOwnerUserId, userId)
            .eq(EsSeal::getStatus, SealStatus.ENABLED));
        for (EsSeal seal : personalSeals) {
            result.add(toAvailableSealVo(seal));
        }
        // 公司章：启用 + 存在一条对本人生效的授权（判定逻辑复用 SealAvailabilityResolver）
        List<EsSeal> companySeals = baseMapper.selectList(Wrappers.<EsSeal>lambdaQuery()
            .eq(EsSeal::getSealType, SealType.COMPANY)
            .eq(EsSeal::getStatus, SealStatus.ENABLED));
        for (EsSeal seal : companySeals) {
            if (SealAvailabilityResolver.isUsable(seal, loadGrants(seal.getSealId()), userId, deptId, bizType)) {
                result.add(toAvailableSealVo(seal));
            }
        }
        return result;
    }

    @Override
    public Long loadPreviewImage(Long sealId) {
        EsSeal seal = baseMapper.selectById(sealId);
        if (seal == null) {
            throw new ServiceException("印章不存在");
        }
        // P1 直接返回 OSS ID 由调用方流式输出；previewOssId 由上传流程生成（低清+水印）。
        // 若 previewOssId 为空则回退原图，生产环境上传流程必须生成预览图。
        Long ossId = seal.getPreviewOssId() != null ? seal.getPreviewOssId() : seal.getSealOssId();
        if (ossId == null) {
            throw new ServiceException("印章图片不存在");
        }
        return ossId;
    }

    /**
     * 印章自身约束校验（类型 / 图片尺寸配对 / 日期窗口 / 名称唯一）。
     *
     * @param bo       印章入参
     * @param existing 已存在的印章（新增时为 null）
     */
    private void validateSealBo(SealBo bo, EsSeal existing) {
        String sealType = bo.getSealType();
        if (SealType.PERSONAL.equals(sealType)) {
            if (bo.getOwnerUserId() == null) {
                throw new ServiceException("个人章必须指定归属用户");
            }
        } else if (SealType.COMPANY.equals(sealType)) {
            if (bo.getOwnerUserId() != null) {
                throw new ServiceException("公司章不允许指定归属用户");
            }
        } else {
            throw new ServiceException("印章类型非法：" + sealType);
        }
        // 「提供了印章图片 ⇒ 必须同时给出宽高」：图片三字段的 @NotNull 只在新增分组(AddGroup)生效，
        // 修改时留空表示沿用原章面（合法），但一旦带图就必须带尺寸，否则会覆盖出尺寸为空/与图不符的脏数据。
        if (bo.getSealOssId() != null && (bo.getImgWidth() == null || bo.getImgHeight() == null)) {
            throw new ServiceException("更换印章图片时必须同时提供印章图片宽度和高度");
        }
        if (bo.getValidFrom() != null && bo.getValidTo() != null
            && bo.getValidFrom().after(bo.getValidTo())) {
            throw new ServiceException("生效日期不能晚于失效日期");
        }
        Long excludeId = existing == null ? null : existing.getSealId();
        Long count = baseMapper.selectCount(Wrappers.<EsSeal>lambdaQuery()
            .eq(EsSeal::getSealName, bo.getSealName())
            .ne(excludeId != null, EsSeal::getSealId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("印章名称【{}】已存在", bo.getSealName());
        }
    }

    /**
     * 构建印章查询条件。
     *
     * @param bo 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<EsSeal> buildQueryWrapper(SealQueryBo bo) {
        LambdaQueryWrapper<EsSeal> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getSealType()), EsSeal::getSealType, bo.getSealType());
        lqw.like(StringUtils.isNotBlank(bo.getSealName()), EsSeal::getSealName, bo.getSealName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EsSeal::getStatus, bo.getStatus());
        if (Boolean.TRUE.equals(bo.getOnlyAvailable())) {
            Long userId = LoginHelper.getUserId();
            // (个人章 且 归属本人) 或 公司章
            lqw.and(w -> w.nested(p -> p.eq(EsSeal::getSealType, SealType.PERSONAL)
                    .eq(EsSeal::getOwnerUserId, userId))
                .or(p -> p.eq(EsSeal::getSealType, SealType.COMPANY)));
        }
        lqw.orderByDesc(EsSeal::getCreateTime);
        return lqw;
    }

    /**
     * 查询印章的全部授权（不预筛状态，授权有效性一律交由 {@link SealAvailabilityResolver} 判定）。
     *
     * @param sealId 印章ID
     * @return 授权列表
     */
    private List<EsSealGrant> loadGrants(Long sealId) {
        return sealGrantMapper.selectList(Wrappers.<EsSealGrant>lambdaQuery()
            .eq(EsSealGrant::getSealId, sealId));
    }

    /**
     * 印章实体 -> 可用印章视图对象。
     *
     * @param seal 印章
     * @return 可用印章视图对象
     */
    private AvailableSealVo toAvailableSealVo(EsSeal seal) {
        AvailableSealVo vo = new AvailableSealVo();
        vo.setSealId(seal.getSealId());
        vo.setSealType(seal.getSealType());
        vo.setSealName(seal.getSealName());
        vo.setPreviewOssId(seal.getPreviewOssId());
        vo.setImgWidth(seal.getImgWidth());
        vo.setImgHeight(seal.getImgHeight());
        return vo;
    }
}
