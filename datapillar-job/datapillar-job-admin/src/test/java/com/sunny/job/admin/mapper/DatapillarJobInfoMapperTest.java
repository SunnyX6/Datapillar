package com.sunny.job.admin.mapper;

import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.scheduler.scheduler.MisfireStrategyEnum;
import com.sunny.job.admin.scheduler.scheduler.ScheduleTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DatapillarJobInfoMapperTest {
	private static Logger logger = LoggerFactory.getLogger(DatapillarJobInfoMapperTest.class);
	
	@Resource
	private DatapillarJobInfoMapper datapillarJobInfoMapper;
	
	@Test
	public void pageList(){
		List<DatapillarJobInfo> list = datapillarJobInfoMapper.pageList(0, 20, 0, -1, null, null, null);
		int list_count = datapillarJobInfoMapper.pageListCount(0, 20, 0, -1, null, null, null);

		logger.info("", list);
		logger.info("", list_count);

		List<DatapillarJobInfo> list2 = datapillarJobInfoMapper.getJobsByGroup(1);
	}
	
	@Test
	public void save_load(){
		DatapillarJobInfo info = new DatapillarJobInfo();
		info.setJobGroup(1);
		info.setJobDesc("desc");
		info.setAuthor("setAuthor");
		info.setAlarmEmail("setAlarmEmail");
		info.setScheduleType(ScheduleTypeEnum.FIX_RATE.name());
		info.setScheduleConf(String.valueOf(33));
		info.setMisfireStrategy(MisfireStrategyEnum.DO_NOTHING.name());
		info.setExecutorRouteStrategy("setExecutorRouteStrategy");
		info.setExecutorHandler("setExecutorHandler");
		info.setExecutorParam("setExecutorParam");
		info.setExecutorBlockStrategy("setExecutorBlockStrategy");
		info.setGlueType("setGlueType");
		info.setGlueSource("setGlueSource");
		info.setGlueRemark("setGlueRemark");
		info.setChildJobId("1");

		info.setAddTime(new Date());
		info.setUpdateTime(new Date());
		info.setGlueUpdatetime(new Date());

		int count = datapillarJobInfoMapper.save(info);

		DatapillarJobInfo info2 = datapillarJobInfoMapper.loadById(info.getId());
		info.setScheduleType(ScheduleTypeEnum.FIX_RATE.name());
		info.setScheduleConf(String.valueOf(44));
		info.setMisfireStrategy(MisfireStrategyEnum.FIRE_ONCE_NOW.name());
		info2.setJobDesc("desc2");
		info2.setAuthor("setAuthor2");
		info2.setAlarmEmail("setAlarmEmail2");
		info2.setExecutorRouteStrategy("setExecutorRouteStrategy2");
		info2.setExecutorHandler("setExecutorHandler2");
		info2.setExecutorParam("setExecutorParam2");
		info2.setExecutorBlockStrategy("setExecutorBlockStrategy2");
		info2.setGlueType("setGlueType2");
		info2.setGlueSource("setGlueSource2");
		info2.setGlueRemark("setGlueRemark2");
		info2.setGlueUpdatetime(new Date());
		info2.setChildJobId("1");

		info2.setUpdateTime(new Date());
		int item2 = datapillarJobInfoMapper.update(info2);

		datapillarJobInfoMapper.delete(info2.getId());

		List<DatapillarJobInfo> list2 = datapillarJobInfoMapper.getJobsByGroup(1);

		int ret3 = datapillarJobInfoMapper.findAllCount();

	}

}
