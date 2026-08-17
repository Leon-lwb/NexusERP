package com.nexus.mes.flash.provider;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.flash.service.IFirmwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 固件文件刷写 Provider (source=FILE)
 * <p>内容来源: mes_firmware(审批发布, 绑工单/物料); 校验: RELEASED + MD5匹配。
 * 从 FlashRecordServiceImpl 下沉的固件 RELEASED 校验与 version/md5 解析逻辑。</p>
 *
 * @author Nexus
 */
@Component
@RequiredArgsConstructor
public class FirmwareFileFlashProvider implements FlashTypeProvider {

    private final FirmwareMapper firmwareMapper;

    @Override
    public String getTypeCode() {
        return "firmware_file";
    }

    @Override
    public String getSourceType() {
        return "FILE";
    }

    @Override
    public String getDisplayName() {
        return "刷写固件";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        Long firmwareId = ctx.getContentRefId();
        if (firmwareId == null) {
            throw new ServiceException("固件ID不能为空(firmware_file 刷写需指定固件)");
        }
        Firmware fw = firmwareMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在: " + firmwareId);
        }
        if (!IFirmwareService.STATUS_RELEASED.equals(fw.getStatus())) {
            throw new ServiceException("仅已发布(RELEASED)固件可刷写: " + fw.getFirmwareCode() + " 当前=" + fw.getStatus());
        }
        return FlashContent.builder()
            .contentRefType("FIRMWARE")
            .contentRefId(fw.getFirmwareId())
            .contentVersion(fw.getVersion())
            .contentChecksum(fw.getFileMd5())
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
        FlashVerifyResult.FlashVerifyResultBuilder b = FlashVerifyResult.builder()
            .pass(true)
            .contentRefType(expected.getContentRefType())
            .contentVersion(expected.getContentVersion())
            .contentChecksum(expected.getContentChecksum());
        // 产线上报实际 checksum 且与应刷不一致 -> MD5 防错判 FAIL
        if (actual != null && actual.getContentChecksum() != null && expected.getContentChecksum() != null
            && !actual.getContentChecksum().equals(expected.getContentChecksum())) {
            b.pass(false).failReason("固件MD5不匹配: 应刷=" + expected.getContentChecksum() + " 实际=" + actual.getContentChecksum());
        }
        return b.build();
    }
}
