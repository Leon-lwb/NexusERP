package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.controller.dto.ImageGenerateRequest;
import com.nexus.common.ai.domain.dto.image.ImageContext;
import com.nexus.common.ai.domain.entity.AiImageRecord;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiImageRecordMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.ai.service.image.IImageGenerationService;
import com.nexus.common.ai.service.image.ImageServiceFactory;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

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

    private final ImageServiceFactory imageServiceFactory;
    private final IAiModelService aiModelService;
    private final AiProperties aiProperties;
    private final AiImageRecordMapper imageRecordMapper;

    /**
     * 生成图片
     * <p>取指定 IMAGE 模型（未指定则取默认 IMAGE 模型）+ 厂商实现 → 调通义万相 → OSS 持久化 → 落库历史记录</p>
     */
    @SaCheckPermission("ai:image:generate")
    @PostMapping("/generate")
    public R<AiImageRecord> generate(@Validated @RequestBody ImageGenerateRequest req) {
        AiModel imageModel = req.getModelId() != null
            ? aiModelService.selectModelById(req.getModelId())
            : aiModelService.selectDefaultImageModel();
        if (imageModel == null) {
            return R.fail("未配置默认图像生成模型（modelType=IMAGE），请先在模型管理中配置");
        }

        IImageGenerationService imageService =
            imageServiceFactory.get(aiProperties.getImage().getDefaultProvider());
        if (imageService == null) {
            return R.fail("未找到文生图厂商: " + aiProperties.getImage().getDefaultProvider());
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
     */
    @SaCheckPermission("ai:image:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        imageRecordMapper.deleteByIds(Arrays.asList(ids));
        return R.ok();
    }
}
