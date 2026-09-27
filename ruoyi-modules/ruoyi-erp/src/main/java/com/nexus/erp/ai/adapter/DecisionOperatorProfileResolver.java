package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.domain.dto.UserDTO;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.DeptService;
import com.nexus.common.core.service.UserService;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 仅使用服务端身份；暂停 ERP 事务，避免绑定连接遮蔽主库。
 */
@Component
@RequiredArgsConstructor
public class DecisionOperatorProfileResolver {
    private final UserService users;
    private final DeptService departments;

    @DS("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public OperatorProfile resolve(DecisionActor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0
            || actor.userName() == null || actor.userName().isBlank()
            || actor.tenantId() == null || actor.tenantId().isBlank() || !TenantHelper.isEnable())
            throw new ServiceException("无法获取可信采购申请人信息");
        return TenantHelper.dynamic(actor.tenantId(), () -> {
            List<UserDTO> found = users.selectListByIds(List.of(actor.userId()));
            if (found == null || found.size() != 1) throw new ServiceException("采购申请人不存在或已停用");
            UserDTO user = found.get(0);
            if (!actor.userId().equals(user.getUserId()) || !actor.userName().equals(user.getUserName())
                || !"0".equals(user.getStatus())) throw new ServiceException("采购申请人身份已变化");
            Long deptId = user.getDeptId();
            String deptName = null;
            if (deptId != null) {
                if (deptId <= 0) throw new ServiceException("采购申请人部门配置无效");
                var names = departments.selectDeptNamesByIds(List.of(deptId));
                deptName = names == null ? null : names.get(deptId);
                if (deptName == null || deptName.isBlank()) throw new ServiceException("采购申请人部门不可访问");
            }
            return new OperatorProfile(user.getUserId(), user.getUserName(), deptId, deptName);
        });
    }

    public record OperatorProfile(Long userId, String userName, Long deptId, String deptName) { }
}
