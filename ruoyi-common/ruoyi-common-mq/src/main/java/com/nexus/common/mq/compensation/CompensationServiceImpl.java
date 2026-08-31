package com.nexus.common.mq.compensation;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Date;

/** 补偿服务实现，通过反射调用补偿方法，支持指数退避重试
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CompensationServiceImpl implements CompensationService {

    private final CompensationRecordMapper compensationRecordMapper;

    /** 注册补偿操作，创建PENDING状态记录
     *
     * @param messageId         关联消息ID
     * @param businessType      业务类型
     * @param businessId        业务ID
     * @param compensationMethod 补偿方法(全限定类名.方法名)
     * @param compensationParams 补偿参数JSON
     */
    @Override
    public void registerCompensation(String messageId, String businessType, String businessId,
                                     String compensationMethod, String compensationParams) {
        CompensationRecord record = new CompensationRecord();
        record.setMessageId(messageId);
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setCompensationStatus("PENDING");
        record.setCompensationMethod(compensationMethod);
        record.setCompensationParams(compensationParams);
        record.setRetryCount(0);
        record.setMaxRetry(5);
        record.setNextRetryTime(new Date(System.currentTimeMillis() + 5000L));
        record.setCreateTime(new Date());
        compensationRecordMapper.insert(record);
    }

    /** 执行补偿操作，通过反射调用补偿方法
     *
     * @param recordId 补偿记录ID
     * @return 是否执行成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean executeCompensation(Long recordId) {
        CompensationRecord record = compensationRecordMapper.selectById(recordId);
        if (record == null) {
            throw new ServiceException("补偿记录不存在");
        }
        try {
            String methodPath = record.getCompensationMethod();
            int lastDot = methodPath.lastIndexOf('.');
            if (lastDot <= 0 || lastDot >= methodPath.length() - 1) {
                throw new ServiceException("补偿方法格式错误: " + methodPath);
            }
            String className = methodPath.substring(0, lastDot);
            String methodName = methodPath.substring(lastDot + 1);

            Class<?> clazz = Class.forName(className);
            Object bean = SpringUtils.getBean(clazz);
            Method method = bean.getClass().getMethod(methodName, String.class);
            method.invoke(bean, record.getCompensationParams());

            markCompensationSuccess(recordId);
            return true;
        } catch (Exception e) {
            log.error("补偿执行失败: recordId={}, error={}", recordId, e.getMessage());
            int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;
            int maxRetry = record.getMaxRetry() != null ? record.getMaxRetry() : 5;
            if (retryCount >= maxRetry) {
                markCompensationFailed(recordId, e.getMessage());
            } else {
                record.setRetryCount(retryCount + 1);
                long delay = (long) Math.pow(2, retryCount) * 1000L;
                record.setNextRetryTime(new Date(System.currentTimeMillis() + delay));
                record.setUpdateTime(new Date());
                compensationRecordMapper.updateById(record);
            }
            return false;
        }
    }

    /** 标记补偿成功
     *
     * @param recordId 补偿记录ID
     */
    @Override
    public void markCompensationSuccess(Long recordId) {
        CompensationRecord record = compensationRecordMapper.selectById(recordId);
        if (record != null) {
            record.setCompensationStatus("SUCCESS");
            record.setUpdateTime(new Date());
            compensationRecordMapper.updateById(record);
        }
    }

    /** 标记补偿失败并记录错误日志
     *
     * @param recordId    补偿记录ID
     * @param errorMessage 错误信息
     */
    @Override
    public void markCompensationFailed(Long recordId, String errorMessage) {
        CompensationRecord record = compensationRecordMapper.selectById(recordId);
        if (record != null) {
            record.setCompensationStatus("FAILED");
            record.setUpdateTime(new Date());
            compensationRecordMapper.updateById(record);
            log.error("补偿最终失败: recordId={}, businessType={}, businessId={}, error={}",
                recordId, record.getBusinessType(), record.getBusinessId(), errorMessage);
        }
    }
}
