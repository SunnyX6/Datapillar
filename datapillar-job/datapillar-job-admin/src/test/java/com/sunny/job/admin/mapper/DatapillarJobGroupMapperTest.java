package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobGroup;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DatapillarJobGroupMapperTest {

    @Resource
    private DatapillarJobGroupMapper datapillarJobGroupMapper;

    @Test
    public void test(){
        List<DatapillarJobGroup> list = datapillarJobGroupMapper.findAll();

        List<DatapillarJobGroup> list2 = datapillarJobGroupMapper.findByAddressType(0);

        DatapillarJobGroup group = new DatapillarJobGroup();
        group.setAppname("setAppName");
        group.setTitle("setTitle");
        group.setAddressType(0);
        group.setAddressList("setAddressList");
        group.setUpdateTime(new Date());

        int ret = datapillarJobGroupMapper.save(group);

        DatapillarJobGroup group2 = datapillarJobGroupMapper.load(group.getId());
        group2.setAppname("setAppName2");
        group2.setTitle("setTitle2");
        group2.setAddressType(2);
        group2.setAddressList("setAddressList2");
        group2.setUpdateTime(new Date());

        int ret2 = datapillarJobGroupMapper.update(group2);

        int ret3 = datapillarJobGroupMapper.remove(group.getId());
    }

}
