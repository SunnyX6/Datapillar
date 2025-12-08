package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobLog;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DatapillarJobLogMapperTest {

    @Resource
    private DatapillarJobLogMapper datapillarJobLogMapper;

    @Test
    public void test(){
        List<DatapillarJobLog> list = datapillarJobLogMapper.pageList(0, 10, 1, 1, null, null, 1);
        int list_count = datapillarJobLogMapper.pageListCount(0, 10, 1, 1, null, null, 1);

        DatapillarJobLog log = new DatapillarJobLog();
        log.setJobGroup(1);
        log.setJobId(1);

        long ret1 = datapillarJobLogMapper.save(log);
        DatapillarJobLog dto = datapillarJobLogMapper.load(log.getId());

        log.setTriggerTime(new Date());
        log.setTriggerCode(1);
        log.setTriggerMsg("1");
        log.setExecutorAddress("1");
        log.setExecutorHandler("1");
        log.setExecutorParam("1");
        ret1 = datapillarJobLogMapper.updateTriggerInfo(log);
        dto = datapillarJobLogMapper.load(log.getId());


        log.setHandleTime(new Date());
        log.setHandleCode(2);
        log.setHandleMsg("2");
        ret1 = datapillarJobLogMapper.updateHandleInfo(log);
        dto = datapillarJobLogMapper.load(log.getId());


        List<Long> ret4 = datapillarJobLogMapper.findClearLogIds(1, 1, new Date(), 100, 100);

        int ret2 = datapillarJobLogMapper.delete(log.getJobId());

    }

}
