package com.nexus.common.sign.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.sign.constants.SealType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SealBo} 校验分组测试。
 *
 * <p>回归背景（Task 12 联调发现）：印章图片字段原为无分组的 {@code @NotNull}，
 * 而新增/修改接口都用默认组校验，导致 PUT 不重传图片（{@code SealVo} 不回传 {@code sealOssId}）
 * 被 400 拒掉。修复后图片三字段只在 {@link AddGroup} 必填，修改走 {@link EditGroup}。</p>
 *
 * @author nexus
 */
class SealBoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    /** 完整的「新增」入参：印章图片三字段齐全。 */
    private static SealBo completeAddBo() {
        SealBo bo = new SealBo();
        bo.setSealType(SealType.PERSONAL);
        bo.setSealName("张三私章");
        bo.setOwnerUserId(7L);
        bo.setSealOssId(1001L);
        bo.setImgWidth(200);
        bo.setImgHeight(200);
        return bo;
    }

    /** 前端的「修改」入参：SealVo 不回传 sealOssId，故图片三字段为空。 */
    private static SealBo editBoWithoutImage() {
        SealBo bo = completeAddBo();
        bo.setSealId(88L);
        bo.setSealOssId(null);
        bo.setImgWidth(null);
        bo.setImgHeight(null);
        return bo;
    }

    private static Set<String> propertyPaths(Set<ConstraintViolation<SealBo>> violations) {
        return violations.stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("新增：AddGroup 下缺少印章图片/宽高，三个字段全部报错且提示语不变")
    void addGroupRequiresImageFields() {
        SealBo bo = editBoWithoutImage();
        Set<ConstraintViolation<SealBo>> violations = validator.validate(bo, AddGroup.class);
        Set<String> paths = propertyPaths(violations);
        assertTrue(paths.containsAll(Set.of("sealOssId", "imgWidth", "imgHeight")),
            "新增时图片三字段应必填，实际违规字段：" + paths);

        String sealOssIdMessage = violations.stream()
            .filter(v -> "sealOssId".equals(v.getPropertyPath().toString()))
            .map(ConstraintViolation::getMessage)
            .findFirst()
            .orElse(null);
        assertEquals("印章图片不能为空", sealOssIdMessage, "新增分组下提示语应保持不变");
    }

    @Test
    @DisplayName("新增：AddGroup 下图片齐全的新增入参无任何违规")
    void addGroupAcceptsCompletePayload() {
        Set<ConstraintViolation<SealBo>> violations = validator.validate(completeAddBo(), AddGroup.class);
        assertTrue(violations.isEmpty(), "完整新增入参不应有违规：" + propertyPaths(violations));
    }

    @Test
    @DisplayName("修改：EditGroup 下不重传印章图片不再报错（本次 Bug 修复回归）")
    void editGroupAllowsMissingImage() {
        Set<ConstraintViolation<SealBo>> violations = validator.validate(editBoWithoutImage(), EditGroup.class);
        Set<String> paths = propertyPaths(violations);
        assertFalse(paths.contains("sealOssId"), "修改时 sealOssId 不应必填：" + paths);
        assertFalse(paths.contains("imgWidth"), "修改时 imgWidth 不应必填：" + paths);
        assertFalse(paths.contains("imgHeight"), "修改时 imgHeight 不应必填：" + paths);
        assertTrue(violations.isEmpty(), "修改入参（仅图片字段为空）应无任何违规：" + paths);
    }

    @Test
    @DisplayName("修改：EditGroup 不校验「图片尺寸配对」，该兜底在服务层 updateByBo")
    void editGroupDoesNotEnforceImageSizePairing() {
        SealBo bo = editBoWithoutImage();
        bo.setSealOssId(2002L);
        Set<String> paths = propertyPaths(validator.validate(bo, EditGroup.class));
        assertFalse(paths.contains("imgWidth"), "换图时的尺寸必填由服务层校验，Bean 校验不再拦截：" + paths);
        assertFalse(paths.contains("imgHeight"), "换图时的尺寸必填由服务层校验，Bean 校验不再拦截：" + paths);
    }

    @Test
    @DisplayName("图片约束已从默认组移出：默认组不再产生图片字段违规")
    void imageConstraintsAreNotInDefaultGroup() {
        Set<String> paths = propertyPaths(validator.validate(editBoWithoutImage()));
        assertFalse(paths.contains("sealOssId"), "默认组不应再校验 sealOssId：" + paths);
        assertFalse(paths.contains("imgWidth"), "默认组不应再校验 imgWidth：" + paths);
        assertFalse(paths.contains("imgHeight"), "默认组不应再校验 imgHeight：" + paths);
    }

    @Test
    @DisplayName("sealName / sealType 在新增与修改两个分组下都必须提供")
    void nameAndTypeRequiredInBothGroups() {
        SealBo bo = completeAddBo();
        bo.setSealName("  ");
        bo.setSealType(null);
        for (Class<?> group : new Class<?>[]{AddGroup.class, EditGroup.class}) {
            Set<String> paths = propertyPaths(validator.validate(bo, group));
            assertTrue(paths.contains("sealName"), group.getSimpleName() + " 下印章名称应必填：" + paths);
            assertTrue(paths.contains("sealType"), group.getSimpleName() + " 下印章类型应必填：" + paths);
        }
    }
}
