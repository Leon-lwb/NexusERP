package com.nexus.common.mq.compensation;

/** 补偿服务接口，提供补偿注册、执行和状态管理
 *
 * @author Nexus
 */
public interface CompensationService {

    /** 注册补偿操作
     *
     * @param messageId         关联消息ID
     * @param businessType      业务类型
     * @param businessId        业务ID
     * @param compensationMethod 补偿方法(全限定类名.方法名)
     * @param compensationParams 补偿参数JSON
     */
    void registerCompensation(String messageId, String businessType, String businessId,
                              String compensationMethod, String compensationParams);

    /** 执行补偿操作
     *
     * @param recordId 补偿记录ID
     * @return 是否执行成功
     */
    boolean executeCompensation(Long recordId);

    /** 标记补偿成功
     *
     * @param recordId 补偿记录ID
     */
    void markCompensationSuccess(Long recordId);

    /** 标记补偿失败
     *
     * @param recordId    补偿记录ID
     * @param errorMessage 错误信息
     */
    void markCompensationFailed(Long recordId, String errorMessage);
}
