package com.nexus.mes.qc;

import com.nexus.mes.qc.domain.bo.QcOqcBo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/** OQC自动编号入参契约回归；Bean校验不能要求客户端提供服务端生成字段。 */
class QcOqcAutoNumberRequestValidationTest {
    private QcOqcBo request() {
        QcOqcBo bo=new QcOqcBo();bo.setSoId(101L);bo.setMaterialId(201L);
        bo.setQuantity(new BigDecimal("22"));bo.setSampleQty(new BigDecimal("3"));return bo;
    }
    @Test void serverGeneratedNumberMayBeOmittedInNormalCreateRequest() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            Validator validator=factory.getValidator();
            assertTrue(validator.validate(request()).isEmpty(),"自动生成编号不能成为正常创建前置必填项");
        }
    }
    @Test void legacyBlankNumberIsNotRequiredForServerGeneratedCreation() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            QcOqcBo bo=request();bo.setOqcNo("");
            assertTrue(factory.getValidator().validate(bo).isEmpty());
        }
    }
    @Test void requiredMaterialIdentityIsNotWeakenedByAutomaticNumbering() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            QcOqcBo bo=request();bo.setMaterialId(null);
            assertTrue(factory.getValidator().validate(bo).stream().anyMatch(x->x.getPropertyPath().toString().equals("materialId")));
        }
    }
}
