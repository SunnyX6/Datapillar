package com.sunny.job.core.executor.impl;

import com.sunny.job.core.executor.DatapillarJobExecutor;
import com.sunny.job.core.handler.annotation.DatapillarJob;
import com.sunny.job.core.handler.impl.MethodJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * datapillar-job executor (for frameless)
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class DatapillarJobSimpleExecutor extends DatapillarJobExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DatapillarJobSimpleExecutor.class);


    private List<Object> datapillarJobBeanList = new ArrayList<>();
    public List<Object> getDatapillarJobBeanList() {
        return datapillarJobBeanList;
    }
    public void setDatapillarJobBeanList(List<Object> datapillarJobBeanList) {
        this.datapillarJobBeanList = datapillarJobBeanList;
    }


    @Override
    public void start() {

        // init JobHandler Repository (for method)
        initJobHandlerMethodRepository(datapillarJobBeanList);

        // super start
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
    }


    private void initJobHandlerMethodRepository(List<Object> datapillarJobBeanList) {
        if (datapillarJobBeanList==null || datapillarJobBeanList.size()==0) {
            return;
        }

        // init job handler from method
        for (Object bean: datapillarJobBeanList) {
            // method
            Method[] methods = bean.getClass().getDeclaredMethods();
            if (methods.length == 0) {
                continue;
            }
            for (Method executeMethod : methods) {
                DatapillarJob datapillarJob = executeMethod.getAnnotation(DatapillarJob.class);
                // registry
                registJobHandler(datapillarJob, bean, executeMethod);
            }

        }

    }

}
