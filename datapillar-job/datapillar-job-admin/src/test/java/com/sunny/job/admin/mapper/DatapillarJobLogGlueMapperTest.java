package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobLogGlue;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DatapillarJobLogGlueMapperTest {

    @Resource
    private DatapillarJobLogGlueMapper datapillarJobLogGlueMapper;

    @Test
    public void test(){
        DatapillarJobLogGlue logGlue = new DatapillarJobLogGlue();
        logGlue.setJobId(1);
        logGlue.setGlueType("1");
        logGlue.setGlueSource("1");
        logGlue.setGlueRemark("1");

        logGlue.setAddTime(new Date());
        logGlue.setUpdateTime(new Date());
        int ret = datapillarJobLogGlueMapper.save(logGlue);

        List<DatapillarJobLogGlue> list = datapillarJobLogGlueMapper.findByJobId(1);

        int ret2 = datapillarJobLogGlueMapper.removeOld(1, 1);

        int ret3 = datapillarJobLogGlueMapper.deleteByJobId(1);
    }

}
