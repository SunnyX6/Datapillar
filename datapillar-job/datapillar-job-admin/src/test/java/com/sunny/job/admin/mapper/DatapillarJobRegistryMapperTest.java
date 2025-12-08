package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobRegistry;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DatapillarJobRegistryMapperTest {

    @Resource
    private DatapillarJobRegistryMapper datapillarJobRegistryMapper;

    @Test
    public void test(){
        int ret = datapillarJobRegistryMapper.registrySaveOrUpdate("g1", "k1", "v1", new Date());
        /*int ret = datapillarJobRegistryDao.registryUpdate("g1", "k1", "v1", new Date());
        if (ret < 1) {
            ret = datapillarJobRegistryDao.registrySave("g1", "k1", "v1", new Date());
        }*/

        List<DatapillarJobRegistry> list = datapillarJobRegistryMapper.findAll(1, new Date());

        int ret2 = datapillarJobRegistryMapper.removeDead(Arrays.asList(1));
    }

    @Test
    public void test2() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            new Thread(()->{
                int ret = datapillarJobRegistryMapper.registrySaveOrUpdate("g1", "k1", "v1", new Date());
                System.out.println(ret);

                /*int ret = datapillarJobRegistryDao.registryUpdate("g1", "k1", "v1", new Date());
                if (ret < 1) {
                    ret = datapillarJobRegistryDao.registrySave("g1", "k1", "v1", new Date());
                }*/
            }).start();
        }

        TimeUnit.SECONDS.sleep(10);
    }

}
