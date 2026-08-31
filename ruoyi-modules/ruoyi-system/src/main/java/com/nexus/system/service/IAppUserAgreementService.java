package com.nexus.system.service;

import com.nexus.system.domain.vo.AppUserAgreementVo;

import java.util.List;

/**
 * App用户协议同意Service接口
 *
 * @author nexus
 * @date 2026-08-05
 */
public interface IAppUserAgreementService {

    /**
     * 记录用户对某协议的同意
     * <p>幂等: 同用户同协议同版本重复提交直接返回;版本不同则更新版本并刷新同意时间</p>
     *
     * @param userId            用户ID
     * @param agreementKey      协议标识
     * @param agreementVersion  协议版本
     * @return 是否新增记录(true新增,false已存在/更新)
     */
    Boolean agree(Long userId, String agreementKey, String agreementVersion);

    /**
     * 查询用户已同意的协议列表
     *
     * @param userId 用户ID
     * @return 已同意协议列表
     */
    List<AppUserAgreementVo> listAgreed(Long userId);

}
