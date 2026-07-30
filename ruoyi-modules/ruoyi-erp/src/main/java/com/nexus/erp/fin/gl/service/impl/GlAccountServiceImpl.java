package com.nexus.erp.fin.gl.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.helper.DataBaseHelper;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlAccountBalance;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.domain.bo.GlAccountBo;
import com.nexus.erp.fin.gl.domain.vo.GlAccountVo;
import com.nexus.erp.fin.gl.mapper.GlAccountBalanceMapper;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlVoucherLineMapper;
import com.nexus.erp.fin.gl.service.IGlAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 会计科目 Service 实现(物化路径树)
 *
 * @author nexus
 * @date 2026-07-29
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class GlAccountServiceImpl implements IGlAccountService {

    private final GlAccountMapper baseMapper;
    private final GlVoucherLineMapper voucherLineMapper;
    private final GlAccountBalanceMapper balanceMapper;

    /** 按查询条件构建 Wrapper 查询列表 */
    @Override
    public List<GlAccountVo> queryList(GlAccountBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /** 根据主键查询科目VO */
    @Override
    public GlAccountVo queryById(Long accountId) {
        return baseMapper.selectVoById(accountId);
    }

    /** 新增科目:计算物化路径;父科目自动标记为非末级 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(GlAccountBo bo) {
        GlAccount entity = MapstructUtils.convert(bo, GlAccount.class);
        entity.setAccountId(IdGeneratorUtil.nextLongId());
        validateAccount(bo, entity);
        fillPathAndLevel(entity);
        if (entity.getStatus() == null) {
            entity.setStatus(GlConstants.NO);
        }
        if (entity.getIsLeaf() == null) {
            entity.setIsLeaf(GlConstants.YES);
        }
        int rows = baseMapper.insert(entity);
        // 父科目由末级变为非末级
        if (entity.getParentId() != null && entity.getParentId() > 0) {
            baseMapper.update(null, Wrappers.<GlAccount>lambdaUpdate()
                .eq(GlAccount::getAccountId, entity.getParentId())
                .set(GlAccount::getIsLeaf, GlConstants.NO));
        }
        return rows > 0;
    }

    /** 修改科目:父级或名称变更时级联刷新子孙物化路径 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(GlAccountBo bo) {
        GlAccount entity = MapstructUtils.convert(bo, GlAccount.class);
        GlAccount old = baseMapper.selectById(entity.getAccountId());
        if (old == null) {
            throw new ServiceException("科目不存在");
        }
        validateAccount(bo, entity);
        fillPathAndLevel(entity);
        int rows = baseMapper.updateById(entity);
        if (rows == 0) {
            throw new ServiceException("科目数据已变更,修改失败,请重试");
        }
        // 父级或名称变更:级联刷新子孙物化路径
        if (!Objects.equals(old.getParentId(), entity.getParentId())
            || !Objects.equals(old.getAccountName(), entity.getAccountName())) {
            updateAccountChildren(old, entity);
        }
        return true;
    }

    /** 删除科目:校验末级/无子科目/无凭证引用/无余额记录 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] accountIds) {
        for (Long id : accountIds) {
            GlAccount acc = baseMapper.selectById(id);
            if (acc == null) {
                continue;
            }
            if (!GlConstants.YES.equals(acc.getIsLeaf())) {
                throw new ServiceException("科目【{}】非末级,不可删除(请先删除子科目)", acc.getAccountCode());
            }
            boolean hasChild = baseMapper.exists(new LambdaQueryWrapper<GlAccount>()
                .eq(GlAccount::getParentId, id));
            if (hasChild) {
                throw new ServiceException("科目【{}】存在子科目,不可删除", acc.getAccountCode());
            }
            boolean voucherRef = voucherLineMapper.exists(new LambdaQueryWrapper<GlVoucherLine>()
                .eq(GlVoucherLine::getAccountId, id));
            if (voucherRef) {
                throw new ServiceException("科目【{}】已被凭证引用,不可删除", acc.getAccountCode());
            }
            boolean hasBalance = balanceMapper.exists(new LambdaQueryWrapper<GlAccountBalance>()
                .eq(GlAccountBalance::getAccountId, id));
            if (hasBalance) {
                throw new ServiceException("科目【{}】存在余额记录,不可删除", acc.getAccountCode());
            }
        }
        return baseMapper.deleteBatchIds(Arrays.asList(accountIds)) > 0;
    }

    /** 查询末级科目(启用、可选按类型过滤),按科目编码排序,供凭证录入下拉 */
    @Override
    public List<GlAccountVo> selectLeafAccounts(String accountType) {
        LambdaQueryWrapper<GlAccount> lqw = Wrappers.lambdaQuery();
        lqw.eq(GlAccount::getIsLeaf, GlConstants.YES);
        lqw.eq(GlAccount::getStatus, GlConstants.NO);
        lqw.eq(StringUtils.isNotBlank(accountType), GlAccount::getAccountType, accountType);
        lqw.orderByAsc(GlAccount::getAccountCode);
        return baseMapper.selectVoList(lqw);
    }

    /** 校验编码唯一:修改时排除自身 */
    @Override
    public Boolean checkAccountCodeUnique(GlAccountBo bo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<GlAccount>()
            .eq(GlAccount::getAccountCode, bo.getAccountCode())
            .ne(ObjectUtil.isNotNull(bo.getAccountId()), GlAccount::getAccountId, bo.getAccountId()));
        return !exist;
    }

    /** 服务端权威计算 path_ids / path_names / level_num */
    private void fillPathAndLevel(GlAccount entity) {
        if (entity.getParentId() == null || entity.getParentId() <= 0L) {
            entity.setLevelNum(1);
            entity.setPathIds(String.valueOf(entity.getAccountId()));
            entity.setPathNames(entity.getAccountName());
            return;
        }
        GlAccount parent = baseMapper.selectById(entity.getParentId());
        if (parent == null) {
            throw new ServiceException("上级科目不存在");
        }
        entity.setLevelNum(parent.getLevelNum() + 1);
        entity.setPathIds(parent.getPathIds() + StringUtils.SEPARATOR + entity.getAccountId());
        entity.setPathNames(parent.getPathNames() + StringUtils.SEPARATOR + entity.getAccountName());
    }

    /** 父级或名称变更后,前缀替换刷新子孙物化路径 */
    private void updateAccountChildren(GlAccount oldX, GlAccount newX) {
        List<GlAccount> children = baseMapper.selectList(new LambdaQueryWrapper<GlAccount>()
            .apply(DataBaseHelper.findInSet(newX.getAccountId(), "path_ids"))
            .ne(GlAccount::getAccountId, newX.getAccountId()));
        if (CollUtil.isEmpty(children)) {
            return;
        }
        int idsLen = oldX.getPathIds().length();
        int namesLen = oldX.getPathNames().length();
        int levelDelta = newX.getLevelNum() - oldX.getLevelNum();
        List<GlAccount> batch = new ArrayList<>();
        for (GlAccount d : children) {
            GlAccount u = new GlAccount();
            u.setAccountId(d.getAccountId());
            u.setPathIds(newX.getPathIds() + d.getPathIds().substring(idsLen));
            u.setPathNames(newX.getPathNames() + d.getPathNames().substring(namesLen));
            u.setLevelNum(d.getLevelNum() + levelDelta);
            batch.add(u);
        }
        baseMapper.updateBatchById(batch);
    }

    /** 校验:编码唯一 / 不以自身为父 / 新父级不能是自身子孙(循环) */
    private void validateAccount(GlAccountBo bo, GlAccount entity) {
        if (!checkAccountCodeUnique(bo)) {
            throw new ServiceException("科目编码'" + bo.getAccountCode() + "'已存在");
        }
        Long cid = entity.getAccountId();
        Long pid = entity.getParentId();
        if (cid != null && cid.equals(pid)) {
            throw new ServiceException("上级科目不能是自己");
        }
        if (cid != null && pid != null && pid > 0L) {
            boolean cycle = baseMapper.exists(new LambdaQueryWrapper<GlAccount>()
                .eq(GlAccount::getAccountId, pid)
                .apply(DataBaseHelper.findInSet(cid, "path_ids")));
            if (cycle) {
                throw new ServiceException("上级科目不能是其子科目,存在循环引用");
            }
        }
    }

    /** 构建查询 Wrapper:按科目编码/名称/类型/状态过滤,按层级+排序号正序 */
    private LambdaQueryWrapper<GlAccount> buildQueryWrapper(GlAccountBo bo) {
        LambdaQueryWrapper<GlAccount> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(StringUtils.isNotBlank(bo.getAccountCode()), GlAccount::getAccountCode, bo.getAccountCode());
            lqw.like(StringUtils.isNotBlank(bo.getAccountName()), GlAccount::getAccountName, bo.getAccountName());
            lqw.eq(StringUtils.isNotBlank(bo.getAccountType()), GlAccount::getAccountType, bo.getAccountType());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), GlAccount::getStatus, bo.getStatus());
        }
        lqw.orderByAsc(GlAccount::getLevelNum);
        lqw.orderByAsc(GlAccount::getSortOrder);
        return lqw;
    }
}
