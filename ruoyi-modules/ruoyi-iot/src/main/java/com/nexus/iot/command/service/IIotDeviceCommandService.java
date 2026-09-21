package com.nexus.iot.command.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.command.domain.IotDeviceCommand;
import com.nexus.iot.command.domain.bo.IotDeviceCommandBo;
import com.nexus.iot.device.domain.IotDevice;

import java.util.Map;

/**
 * IoT下行指令Service接口（IOT-PIPE-02 下行通道）。
 * <p>
 * 能力边界（如实说明）：服务端**登记 + 通过 Moquette 内部发布**到设备 Topic，
 * 并记录下发结果与设备回执；不实现指令超时自动判定（需设备在线状态与定时扫描配合，
 * 见报告"未完成项"），也不实现设备影子/OTA（后续批次）。
 *
 * @author nexus
 */
public interface IIotDeviceCommandService {

    /**
     * 分页查询指令留痕。
     *
     * @param deviceId  设备ID（可空）
     * @param status    状态（可空）
     * @param pageQuery 分页参数
     * @return 指令分页结果
     */
    TableDataInfo<IotDeviceCommand> selectPageList(Long deviceId, String status, PageQuery pageQuery);

    /**
     * 查询指令详情。
     *
     * @param commandId 指令ID
     * @return 指令实体，不存在返回 null
     */
    IotDeviceCommand selectById(Long commandId);

    /**
     * 下发属性设置指令（Topic: {@code iot/{tenant}/{deviceCode}/property/set}）。
     *
     * @param bo 指令业务对象
     * @return 落库后的指令（含状态与结果说明）
     */
    IotDeviceCommand sendPropertySet(IotDeviceCommandBo bo);

    /**
     * 下发服务调用指令（Topic: {@code iot/{tenant}/{deviceCode}/event/set}）。
     *
     * @param bo 指令业务对象（serviceName 必填）
     * @return 落库后的指令（含状态与结果说明）
     */
    IotDeviceCommand sendServiceInvoke(IotDeviceCommandBo bo);

    /**
     * 重发指令（仅允许对未成功/失败的指令重发；成功后不可重发）。
     *
     * @param commandId 指令ID
     * @return 重发后的指令
     */
    IotDeviceCommand resend(Long commandId);

    /**
     * 消费端入口：处理设备回执（{@code property/set_reply}），更新指令状态与回执载荷。
     * <p>
     * 幂等：以回执的 Stream entry id 作为 {@code ack_msg_id}，重复回执不再改写（CAS）。
     *
     * @param device   设备记录（权威身份）
     * @param entry    回执 entry（含 command_id / success / message / payload）
     * @param streamId Stream entry id
     */
    void handleReply(IotDevice device, Map<Object, Object> entry, String streamId);
}
