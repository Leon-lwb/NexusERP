package com.nexus.erp.wm.controller.serial;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.aspectj.AspectJPrecedenceInformation;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 原PDA幂等切面必须在ERP事务外：提交完成才可置DONE，重放无需重复CAS。
 */
@Component
public class WmSerialAdvisorOrder implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof Advised advised)) {
            return bean;
        }
        int serial = -1, pda = -1;
        var advisors = advised.getAdvisors();
        for (int i = 0; i < advisors.length; i++) {
            if (advisors[i] instanceof AspectJPrecedenceInformation info) {
                if ("wmSerialBusinessAspect".equals(info.getAspectName())) {
                    serial = i;
                }
                if ("pdaIdempotentAspect".equals(info.getAspectName())) {
                    pda = i;
                }
            }
        }
        if (serial >= 0 && pda > serial) {
            var idempotent = advisors[pda];
            advised.removeAdvisor(pda);
            advised.addAdvisor(serial, idempotent);
        }
        return bean;
    }
}
