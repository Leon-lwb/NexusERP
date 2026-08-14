package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.domain.dto.ImageGenerateRequest;
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

    /** 文生图厂商服务工厂，按厂商名获取具体实现（如通义万相）。 */
    private final ImageServiceFactory imageServiceFactory;
    /** AI 模型业务服务，用于解析图像模型配置与默认模型。 */
    private final IAiModelService aiModelService;
    /** AI 配置属性，提供默认文生图厂商等运行参数。 */
    private final AiProperties aiProperties;
    /** 文生图历史记录表 Mapper，负责记录持久化与查询。 */
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
        // 越权防护：仅可删除属于自己的历史记录
        Long userId = LoginHelper.getUserId();
        List<AiImageRecord> records = imageRecordMapper.selectList(
            new LambdaQueryWrapper<AiImageRecord>()
                .in(AiImageRecord::getId, Arrays.asList(ids)));
        for (AiImageRecord record : records) {
            if (record == null || !userId.equals(record.getUserId())) {
                return R.fail("无权删除他人记录");
            }
        }
        imageRecordMapper.deleteByIds(Arrays.asList(ids));
        return R.ok();
    }
}
