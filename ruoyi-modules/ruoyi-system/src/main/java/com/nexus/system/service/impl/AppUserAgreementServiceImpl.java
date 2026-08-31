package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.system.domain.AppUserAgreement;
import com.nexus.system.domain.vo.AppUserAgreementVo;
import com.nexus.system.mapper.AppUserAgreementMapper;
import com.nexus.system.service.IAppUserAgreementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * App用户协议同意Service实现
 *
 * @author nexus
 * @date 2026-08-05
 */
@Service
@RequiredArgsConstructor
public class AppUserAgreementServiceImpl extends ServiceImpl<AppUserAgreementMapper, AppUserAgreement> implements IAppUserAgreementService {

    private final AppUserAgreementMapper appUserAgreementMapper;

    @Override
    public Boolean agree(Long userId, String agreementKey, String agreementVersion) {
        LambdaQueryWrapper<AppUserAgreement> lqw = Wrappers.lambdaQuery();
        lqw.eq(AppUserAgreement::getUserId, userId)
            .eq(AppUserAgreement::getAgreementKey, agreementKey);
        AppUserAgreement existing = appUserAgreementMapper.selectOne(lqw);
        if (existing != null) {
            // 版本相同: 幂等直接返回; 版本不同: 视为重新同意,更新版本并刷新同意时间
            if (agreementVersion.equals(existing.getAgreementVersion())) {
                return false;
            }
            existing.setAgreementVersion(agreementVersion);
            existing.setAgreeTime(new Date());
            appUserAgreementMapper.updateById(existing);
            return false;
        }
        AppUserAgreement entity = new AppUserAgreement();
        entity.setUserId(userId);
        entity.setAgreementKey(agreementKey);
        entity.setAgreementVersion(agreementVersion);
        entity.setAgreeTime(new Date());
        appUserAgreementMapper.insert(entity);
        return true;
    }

    @Override
    public List<AppUserAgreementVo> listAgreed(Long userId) {
        LambdaQueryWrapper<AppUserAgreement> lqw = Wrappers.lambdaQuery();
        lqw.eq(AppUserAgreement::getUserId, userId)
            .orderByDesc(AppUserAgreement::getAgreeTime);
        return appUserAgreementMapper.selectVoList(lqw);
    }

}
