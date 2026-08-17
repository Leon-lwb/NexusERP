package com.nexus.mes.flash.provider;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.flash.domain.MesContentFile;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.mapper.MesContentFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 配置文件刷写 Provider (source=FILE)
 * <p>内容来源: mes_content_file(审批发布); 校验: RELEASED + 物料匹配 + MD5。
 * 与 FirmwareFileFlashProvider 同构, 仅内容源表不同。</p>
 *
 * @author Nexus
 */
@Component
@RequiredArgsConstructor
public class ConfigFileFlashProvider implements FlashTypeProvider {

    private static final String STATUS_RELEASED = "RELEASED";

    private final MesContentFileMapper contentFileMapper;

    @Override
    public String getTypeCode() {
        return "config_file";
    }

    @Override
    public String getSourceType() {
        return "FILE";
    }

    @Override
    public String getDisplayName() {
        return "刷写配置文件";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        Long contentFileId = ctx.getContentRefId();
        if (contentFileId == null) {
            throw new ServiceException("配置文件ID不能为空(config_file 刷写需指定配置文件)");
        }
        MesContentFile cf = contentFileMapper.selectById(contentFileId);
        if (cf == null) {
            throw new ServiceException("配置文件不存在: " + contentFileId);
        }
        if (!STATUS_RELEASED.equals(cf.getStatus())) {
            throw new ServiceException("仅已发布(RELEASED)配置文件可刷写: " + cf.getContentCode() + " 当前=" + cf.getStatus());
        }
        if (ctx.getSnMaterialId() != null && !ctx.getSnMaterialId().equals(cf.getMaterialId())) {
            throw new ServiceException("配置文件物料与SN物料不匹配: 配置物料ID=" + cf.getMaterialId() + " SN物料ID=" + ctx.getSnMaterialId());
        }
        return FlashContent.builder()
            .contentRefType("CONFIG")
            .contentRefId(cf.getContentFileId())
            .contentVersion(cf.getVersion())
            .contentChecksum(cf.getFileMd5())
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
        FlashVerifyResult.FlashVerifyResultBuilder b = FlashVerifyResult.builder()
            .pass(true)
            .contentRefType(expected.getContentRefType())
            .contentVersion(expected.getContentVersion())
            .contentChecksum(expected.getContentChecksum());
        if (actual != null && actual.getContentChecksum() != null && expected.getContentChecksum() != null
            && !actual.getContentChecksum().equals(expected.getContentChecksum())) {
            b.pass(false).failReason("配置文件MD5不匹配: 应刷=" + expected.getContentChecksum() + " 实际=" + actual.getContentChecksum());
        }
        return b.build();
    }
}
