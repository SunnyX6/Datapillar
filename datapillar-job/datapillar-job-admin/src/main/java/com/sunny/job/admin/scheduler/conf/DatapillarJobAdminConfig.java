package com.sunny.job.admin.scheduler.conf;

import com.sunny.job.admin.scheduler.alarm.JobAlarmer;
import com.sunny.job.admin.scheduler.scheduler.DatapillarJobScheduler;
import com.sunny.job.admin.mapper.*;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * datapillar-job config
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */

@Component
public class DatapillarJobAdminConfig implements InitializingBean, DisposableBean {

    private static DatapillarJobAdminConfig adminConfig = null;
    public static DatapillarJobAdminConfig getAdminConfig() {
        return adminConfig;
    }


    // ---------------------- DatapillarJobScheduler ----------------------

    private DatapillarJobScheduler datapillarJobScheduler;

    @Override
    public void afterPropertiesSet() throws Exception {
        adminConfig = this;

        datapillarJobScheduler = new DatapillarJobScheduler();
        datapillarJobScheduler.init();
    }

    @Override
    public void destroy() throws Exception {
        datapillarJobScheduler.destroy();
    }


    // ---------------------- DatapillarJobScheduler ----------------------

    // conf
    @Value("${datapillar.job.i18n}")
    private String i18n;

    @Value("${datapillar.job.accessToken}")
    private String accessToken;

    @Value("${datapillar.job.timeout}")
    private int timeout;

    @Value("${spring.mail.from}")
    private String emailFrom;

    @Value("${datapillar.job.triggerpool.fast.max}")
    private int triggerPoolFastMax;

    @Value("${datapillar.job.triggerpool.slow.max}")
    private int triggerPoolSlowMax;

    @Value("${datapillar.job.logretentiondays}")
    private int logretentiondays;

    // dao, service

    @Resource
    private DatapillarJobLogMapper datapillarJobLogMapper;
    @Resource
    private DatapillarJobInfoMapper datapillarJobInfoMapper;
    @Resource
    private DatapillarJobRegistryMapper datapillarJobRegistryMapper;
    @Resource
    private DatapillarJobGroupMapper datapillarJobGroupMapper;
    @Resource
    private DatapillarJobLogReportMapper datapillarJobLogReportMapper;
    @Resource
    private DatapillarJobWorkflowMapper datapillarJobWorkflowMapper;
    @Resource
    private JavaMailSender mailSender;
    @Resource
    private DataSource dataSource;
    @Resource
    private JobAlarmer jobAlarmer;
    @Resource
    private com.sunny.job.admin.dag.WorkflowExecutor workflowExecutor;


    public String getI18n() {
        if (!Arrays.asList("zh_CN", "zh_TC", "en").contains(i18n)) {
            return "zh_CN";
        }
        return i18n;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public int getTimeout() {
        return timeout;
    }

    public String getEmailFrom() {
        return emailFrom;
    }

    public int getTriggerPoolFastMax() {
        if (triggerPoolFastMax < 200) {
            return 200;
        }
        return triggerPoolFastMax;
    }

    public int getTriggerPoolSlowMax() {
        if (triggerPoolSlowMax < 100) {
            return 100;
        }
        return triggerPoolSlowMax;
    }

    public int getLogretentiondays() {
        if (logretentiondays < 3) {
            return -1;  // Limit greater than or equal to 3, otherwise close
        }
        return logretentiondays;
    }

    public DatapillarJobLogMapper getDatapillarJobLogMapper() {
        return datapillarJobLogMapper;
    }

    public DatapillarJobInfoMapper getDatapillarJobInfoMapper() {
        return datapillarJobInfoMapper;
    }

    public DatapillarJobRegistryMapper getDatapillarJobRegistryMapper() {
        return datapillarJobRegistryMapper;
    }

    public DatapillarJobGroupMapper getDatapillarJobGroupMapper() {
        return datapillarJobGroupMapper;
    }

    public DatapillarJobLogReportMapper getDatapillarJobLogReportMapper() {
        return datapillarJobLogReportMapper;
    }

    public DatapillarJobWorkflowMapper getDatapillarJobWorkflowMapper() {
        return datapillarJobWorkflowMapper;
    }

    public JavaMailSender getMailSender() {
        return mailSender;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public JobAlarmer getJobAlarmer() {
        return jobAlarmer;
    }

    public com.sunny.job.admin.dag.WorkflowExecutor getWorkflowExecutor() {
        return workflowExecutor;
    }

}
