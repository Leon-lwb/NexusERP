package com.nexus.common.sign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.nexus.common.sign.domain.bo.SealGrantBo;
import com.nexus.common.sign.domain.vo.SealGrantVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.service.IESSealGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用章授权服务实现。
 *
 * <p>治理层职责：只有<b>公司章</b>需要授权（个人章仅归属人本人可用），
 * 故本类在新增/修改时强制校验「印章存在 + 类型为公司章 + 未销毁」，
 * 授权范围（人/部门/业务场景/时间窗）的有效性判定仍由 {@code SealAvailabilityResolver} 在用时完成。</p>
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class EsSealGrantServiceImpl implements IESSealGrantService {

    /** 授权记录默认状态：启用 */
    private static final String DEFAULT_STATUS = "1";

    /** 用章授权 Mapper（主表） */
    private final EsSealGrantMapper baseMapper;

    /** 印章 Mapper（校验印章存在性/类型/状态，并批量回填印章名称） */
    private final EsSealMapper sealMapper;

    @Override
    public TableDataInfo<SealGrantVo> selectPageList(SealGrantBo bo, PageQuery pageQuery) {
        Page<SealGrantVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(bo));
        fillSealName(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<SealGrantVo> selectListBySeal(Long sealId) {
        List<SealGrantVo> list = baseMapper.selectVoList(Wrappers.<EsSealGrant>lambdaQuery()
            .eq(EsSealGrant::getSealId, sealId)
            .orderByDesc(EsSealGrant::getCreateTime));
        fillSealName(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(SealGrantBo bo) {
        validate(bo, null);
        EsSealGrant entity = MapstructUtils.convert(bo, EsSealGrant.class);
        entity.setGrantBy(LoginHelper.getUserId());
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(DEFAULT_STATUS);
        }
        baseMapper.insert(entity);
        return entity.getGrantId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SealGrantBo bo) {
        EsSealGrant existing = baseMapper.selectById(bo.getGrantId());
        if (existing == null) {
            throw new ServiceException("授权记录不存在");
        }
        validate(bo, existing);
        EsSealGrant entity = MapstructUtils.convert(bo, EsSealGrant.class);
        // 被授权用户 / 被授权部门二选一。updateById 会跳过 null 字段：把「按部门」的授权改成「按用户」时，
        // 行里旧的 grantee_dept_id 会原样留下，得到一条「既匹配该用户、又匹配整个部门」的授权 ——
        // 实际能用章的人比管理员预期更多。故缺席的那一列改由 wrapper 显式 SET 为 NULL，
        // 不再依赖 updateById 的 null 跳过（其余字段仍走 entity 的 NOT_NULL 语义与自动填充）。
        LambdaUpdateWrapper<EsSealGrant> luw = Wrappers.<EsSealGrant>lambdaUpdate()
            .eq(EsSealGrant::getGrantId, entity.getGrantId())
            .set(entity.getGranteeUserId() == null, EsSealGrant::getGranteeUserId, null)
            .set(entity.getGranteeDeptId() == null, EsSealGrant::getGranteeDeptId, null);
        return baseMapper.update(entity, luw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(List<Long> grantIds) {
        if (grantIds == null || grantIds.isEmpty()) {
            return 0;
        }
        return baseMapper.deleteByIds(grantIds);
    }

    /**
     * 授权业务校验。
     *
     * @param bo       授权入参
     * @param existing 既有授权记录（新增时传 null；当前规则与场景无关，保留以区分新增/修改语义）
     */
    private void validate(SealGrantBo bo, EsSealGrant existing) {
        if (bo.getGranteeUserId() == null && bo.getGranteeDeptId() == null) {
            throw new ServiceException("被授权用户与被授权部门至少填写一项");
        }
        if (bo.getValidFrom() != null && bo.getValidTo() != null
            && bo.getValidFrom().after(bo.getValidTo())) {
            throw new ServiceException("授权生效时间不能晚于失效时间");
        }
        // 授权只针对公司章：个人章仅归属人本人可用，无需也无法授权
        EsSeal seal = sealMapper.selectById(bo.getSealId());
        if (seal == null) {
            throw new ServiceException("印章不存在");
        }
        if (!SealType.COMPANY.equals(seal.getSealType())) {
            throw new ServiceException("仅公司章需要授权，个人章仅归属人本人可用");
        }
        if (SealStatus.DESTROYED.equals(seal.getStatus())) {
            throw new ServiceException("印章已销毁，不可再授权");
        }
    }

    /**
     * 构建授权查询条件。
     *
     * @param bo 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<EsSealGrant> buildWrapper(SealGrantBo bo) {
        LambdaQueryWrapper<EsSealGrant> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getSealId() != null, EsSealGrant::getSealId, bo.getSealId());
        lqw.eq(bo.getGranteeUserId() != null, EsSealGrant::getGranteeUserId, bo.getGranteeUserId());
        lqw.eq(bo.getGranteeDeptId() != null, EsSealGrant::getGranteeDeptId, bo.getGranteeDeptId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EsSealGrant::getStatus, bo.getStatus());
        lqw.orderByDesc(EsSealGrant::getCreateTime);
        return lqw;
    }

    /**
     * 批量回填印章名称（一次 IN 查询，避免 N+1）。
     *
     * <p>{@code sealName} 无翻译器可用（语义为「印章ID → 印章名称」，非字典/用户/部门），
     * 只能由服务层在此填充；{@code granteeUserName} / {@code granteeDeptName}
     * 由 VO 上的 {@code @Translation} 在序列化时自动翻译，此处不重复处理。</p>
     *
     * @param records 授权视图对象列表（可为空）
     */
    private void fillSealName(List<SealGrantVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> sealIds = new LinkedHashSet<>();
        for (SealGrantVo vo : records) {
            if (vo.getSealId() != null) {
                sealIds.add(vo.getSealId());
            }
        }
        if (sealIds.isEmpty()) {
            return;
        }
        Map<Long, String> sealNameMap = new HashMap<>();
        for (EsSeal seal : sealMapper.selectByIds(sealIds)) {
            sealNameMap.put(seal.getSealId(), seal.getSealName());
        }
        for (SealGrantVo vo : records) {
            vo.setSealName(sealNameMap.get(vo.getSealId()));
        }
    }
}
