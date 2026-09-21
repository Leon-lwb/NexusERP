package com.nexus.mes.dhr.support;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.service.ESignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DHR 对电子签名台账（{@code es_signature}，<b>只存在于 master / ry-vue</b>）的访问出口。
 *
 * <p><b>为什么需要这个薄封装（勿删）：</b>DHR 业务服务类是 {@code @DS("mes")}，而签名台账在
 * master。dynamic-datasource 的 @DS 切面 order 为 {@code HIGHEST_PRECEDENCE}，即比
 * {@code @Transactional} 更外层 —— <b>只有「开事务、取连接」之前压栈的 master 键才真正生效</b>。
 * 一旦调用方已经开着 ry-mes 事务（如 DHR 构建/重建时聚合 SOP 分册的签名摘要），
 * 直接调 {@code ESignatureService} 的普通方法会加入该事务、连接已绑定在 ry-mes，
 * 报 {@code Table 'ry-mes.es_signature' doesn't exist}。</p>
 *
 * <p>因此本类：① 类级 {@code @DS("master")}；② 每个公开方法 {@code REQUIRES_NEW} 挂起调用方事务、
 * 在 master 上自开新事务。这样无论调用方是否处于 MES 事务内，签名台账读写都落在 ry-vue。
 * 与 {@code ESignatureServiceImpl#signInNewTransaction} 是同一处理方式。</p>
 *
 * <p><b>另一条约束：</b>DHR 的 {@code /signatures} 与 {@code /verify} 读方法本身
 * <b>不得</b>标 {@code @Transactional}（无事务时每次 mapper 调用各自取连接、@DS 按次切换，
 * 跨库读才成立）。本封装只负责签名侧的库边界。</p>
 *
 * @author Nexus
 */
@Service
@RequiredArgsConstructor
@DS("master")
public class DhrSignatureAccessor {

    private final ESignatureService eSignatureService;

    /**
     * 按业务类型 + 业务ID查询签名链（master）。
     *
     * @param bizType 业务类型（见 SignBizType）
     * @param bizId   业务ID
     * @return 签名列表（签署时间正序）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<SignatureVo> listByBiz(String bizType, Long bizId) {
        return eSignatureService.listByBiz(bizType, bizId);
    }

    /**
     * 校验指定业务的签名链（master）。
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 验签结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public VerifyResultVo verifyChain(String bizType, Long bizId) {
        return eSignatureService.verifyChain(bizType, bizId);
    }
}
