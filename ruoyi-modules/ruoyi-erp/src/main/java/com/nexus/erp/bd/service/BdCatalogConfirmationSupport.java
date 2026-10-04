package com.nexus.erp.bd.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;

import java.math.BigDecimal;
import java.util.Objects;

/** 确认与普通编辑共用的当前操作者校验，不接受请求中的租户或审计身份。 */
public final class BdCatalogConfirmationSupport {
    private BdCatalogConfirmationSupport() { }

    public record ActorContext(String tenantId, Long actorId) { }

    public static ActorContext currentActor() {
        String tenant = TenantHelper.getTenantId();
        Long actor = LoginHelper.getUserId();
        require(TenantHelper.isEnable() && tenant != null && tenant.matches("[0-9]+")
            && actor != null && actor > 0, "确认资料必须有已启用的当前租户与登录操作者");
        return new ActorContext(tenant, actor);
    }

    public static void checkActor(ActorContext expected) {
        require(expected.equals(currentActor()), "确认期间当前租户或操作者已改变");
    }

    /** 普通编辑沿用非空字段更新合同；数值表示方式变化不视为事实变化。 */
    public static boolean changed(Object existing, Object proposed) {
        if (proposed == null) return false;
        if (existing instanceof BigDecimal oldDecimal && proposed instanceof BigDecimal newDecimal) {
            return oldDecimal.compareTo(newDecimal) != 0;
        }
        return !Objects.equals(existing, proposed);
    }

    public static void require(boolean valid, String reason) {
        if (!valid) throw new ServiceException(reason);
    }
}
