package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.ai.domain.dto.ImageGenerateRequest;
import com.nexus.ai.service.model.AiModelCapabilitySelection;
import com.nexus.common.ai.domain.dto.image.ImageContext;
import com.nexus.common.ai.domain.entity.AiImageRecord;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiImageRecordMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.ai.service.image.IImageGenerationService;
import com.nexus.common.ai.service.image.ImageServiceFactory;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * AI 文生图 Controller
 * <p>独立文生图入口：生成图片（通义万相 + OSS 持久化）+ 历史记录查询/删除</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/image")
public class AiImageController extends BaseController {

    /** 文生图厂商服务工厂，按所选模型的厂商类型获取具体实现（如通义万相）。 */
    private final ImageServiceFactory imageServiceFactory;
    /** AI 模型业务服务，用于解析图像模型配置与默认模型。 */
    private final IAiModelService aiModelService;
    /** 文生图历史记录表 Mapper，负责记录持久化与查询。 */
    private final AiImageRecordMapper imageRecordMapper;

    /**
     * 生成图片
     * <p>取指定 IMAGE 模型（未指定则取默认 IMAGE 模型）→ 能力校验 → 按该模型厂商类型路由文生图实现
     * → 调厂商 API → OSS 持久化 → 落库历史记录</p>
     */
    @SaCheckPermission("ai:image:generate")
    @PostMapping("/generate")
    public R<AiImageRecord> generate(@Validated @RequestBody ImageGenerateRequest req) {
        AiModel imageModel;
        if (req.getModelId() != null) {
            imageModel = aiModelService.selectModelById(req.getModelId());
            if (imageModel == null) {
                throw new ServiceException("所选模型不存在（id=" + req.getModelId() + "）");
            }
        } else {
            imageModel = aiModelService.selectDefaultImageModel();
            if (imageModel == null) {
                throw new ServiceException("未配置默认图像生成模型（modelType=IMAGE），请先在模型管理中配置");
            }
        }
        if (!AiModelCapabilitySelection.supportsImageGeneration(imageModel)) {
            throw new ServiceException("模型「" + imageModel.getDisplayName() + "」不具备文生图能力"
                + "（需 IMAGE 类型且输入能力含 TEXT、输出能力含 IMAGE，当前 input="
                + imageModel.getInputCapabilities() + ", output=" + imageModel.getOutputCapabilities()
                + "），请在「AI 模型配置」补全能力或更换图像生成模型");
        }

        // 跟随所选模型的厂商类型路由（providerType 为模型加载时填充的瞬态字段），
        // 不再使用全局默认厂商——否则换模型不换厂商会打错上游
        IImageGenerationService imageService = imageServiceFactory.get(imageModel.getProviderType());
        if (imageService == null) {
            throw new ServiceException("模型「" + imageModel.getDisplayName() + "」所属厂商（"
                + imageModel.getProviderType() + "）未注册文生图实现，"
                + "请检查模型与厂商配置（当前支持: DASHSCOPE/通义万相）");
        }

        ImageContext ctx = ImageContext.builder()
            .modelConfig(imageModel)
            .prompt(req.getPrompt())
            .size(req.getSize())
            .build();

        String imageUrl = imageService.generateImage(ctx);

        AiImageRecord record = new AiImageRecord();
        record.setUserId(LoginHelper.getUserId());
        record.setPrompt(req.getPrompt());
        record.setImageUrl(imageUrl);
        record.setSize(req.getSize());
        record.setModelId(imageModel.getId());
        record.setModelName(imageModel.getModelName());
        imageRecordMapper.insert(record);

        return R.ok(record);
    }

    /**
     * 查询当前用户的文生图历史
     */
    @SaCheckPermission("ai:image:list")
    @GetMapping("/list")
    public TableDataInfo<AiImageRecord> list(PageQuery pageQuery) {
        LambdaQueryWrapper<AiImageRecord> lqw = new LambdaQueryWrapper<>();
        lqw.eq(AiImageRecord::getUserId, LoginHelper.getUserId());
        lqw.orderByDesc(AiImageRecord::getCreateTime);
        Page<AiImageRecord> page = imageRecordMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 删除文生图历史记录
     * <p>越权防护：仅允许删除当前登录用户自己的记录</p>
     */
    @SaCheckPermission("ai:image:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        List<Long> idList = Arrays.asList(ids);
        List<AiImageRecord> records = imageRecordMapper.selectByIds(idList);
        Long userId = LoginHelper.getUserId();
        for (AiImageRecord record : records) {
            if (record == null || !userId.equals(record.getUserId())) {
                return R.fail("无权删除他人记录");
            }
        }
        imageRecordMapper.deleteByIds(idList);
        return R.ok();
    }
}
