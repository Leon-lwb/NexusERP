package com.nexus.iot.event.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.event.domain.IotDeviceEvent;
import com.nexus.iot.event.domain.bo.IotDeviceEventBo;

import java.util.List;
import java.util.Map;

/**
 * IoT设备事件Service接口（IOT-PIPE-02 上行事件）。
 *
 * @author nexus
 */
public interface IIotDeviceEventService {

    /**
     * 分页查询设备事件。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 事件分页结果
     */
    TableDataInfo<IotDeviceEvent> selectPageList(IotDeviceEventBo bo, PageQuery pageQuery);

    /**
     * 查询设备事件列表（不分页，供导出）。
     *
     * @param bo 查询条件
     * @return 事件列表
     */
    List<IotDeviceEvent> selectList(IotDeviceEventBo bo);

    /**
     * 查询事件详情。
     *
     * @param eventId 事件ID
     * @return 事件实体，不存在返回 null
     */
    IotDeviceEvent selectById(Long eventId);

    /**
     * 标记事件处理状态（CAS，0 行抛业务异常）。
     *
     * @param eventId       事件ID
     * @param processStatus PROCESSED/IGNORED/FAILED
     * @param remark        处理说明
     * @return 是否成功
     */
    boolean markProcessed(Long eventId, String processStatus, String remark);

    /**
     * 消费端入口：落库一条设备事件（幂等：source_msg_id 唯一键）+ 告警类事件联动 IoT 告警。
     * <p>
     * 运行在无登录上下文的消费线程，内部用 {@code TenantHelper.dynamic} 传播租户。
     *
     * @param device   设备记录（权威身份）
     * @param entry    事件流 entry
     * @param streamId Redis Stream entry id（幂等去重键）
     * @return 落库后的事件ID；重复消息（唯一键冲突）返回 null
     */
    Long persistEvent(IotDevice device, Map<Object, Object> entry, String streamId);
}
