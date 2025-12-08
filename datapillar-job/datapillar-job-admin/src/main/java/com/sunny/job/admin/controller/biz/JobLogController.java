package com.sunny.job.admin.controller.biz;

import com.sunny.job.admin.model.DatapillarJobGroup;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobLog;
import com.sunny.job.admin.service.DatapillarJobLogService;
import com.sunny.job.admin.util.StringTool;
import com.sunny.job.core.biz.model.LogResult;
import com.sunny.job.core.biz.model.ReturnT;
import com.sunny.job.core.util.DateUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * index controller
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Controller
@RequestMapping("/log")
public class JobLogController {
	private static final Logger logger = LoggerFactory.getLogger(JobLogController.class);

	@Resource
	private DatapillarJobLogService datapillarJobLogService;

	@RequestMapping
	public String index(HttpServletRequest request, Model model,
						@RequestParam(value = "jobGroup", required = false, defaultValue = "0") Integer jobGroup,
						@RequestParam(value = "jobId", required = false, defaultValue = "0") Integer jobId) {

		ReturnT<Object[]> result = datapillarJobLogService.loadLogPageData(jobGroup, jobId);
		if (result.getCode() != ReturnT.SUCCESS_CODE) {
			throw new RuntimeException(result.getMsg());
		}

		Object[] data = result.getContent();
		@SuppressWarnings("unchecked")
		List<DatapillarJobGroup> jobGroupList = (List<DatapillarJobGroup>) data[0];
		jobGroup = (Integer) data[1];
		jobId = (Integer) data[2];
		@SuppressWarnings("unchecked")
		List<DatapillarJobInfo> jobInfoList = (List<DatapillarJobInfo>) data[3];

		model.addAttribute("JobGroupList", jobGroupList);
		model.addAttribute("jobInfoList", jobInfoList);
		model.addAttribute("jobGroup", jobGroup);
		model.addAttribute("jobId", jobId);

		return "joblog/joblog.index";
	}

	/*@RequestMapping("/getJobsByGroup")
	@ResponseBody
	public ReturnT<List<DatapillarJobInfo>> getJobsByGroup(HttpServletRequest request, @RequestParam("jobGroup") int jobGroup){

		// valid permission
		JobInfoController.validJobGroupPermission(request, jobGroup);

		// query
		List<DatapillarJobInfo> list = datapillarJobInfoMapper.getJobsByGroup(jobGroup);
		return ReturnT.ofSuccess(list);
	}*/
	
	@RequestMapping("/list/page")
	@ResponseBody
	public Map<String, Object> pageList(HttpServletRequest request,
										@RequestParam(value = "start", required = false, defaultValue = "0") int start,
										@RequestParam(value = "length", required = false, defaultValue = "10") int length,
										@RequestParam("jobGroup") int jobGroup,
										@RequestParam("jobId") int jobId,
										@RequestParam("logStatus") int logStatus,
										@RequestParam("filterTime") String filterTime) {

		// 解析参数
		Date triggerTimeStart = null;
		Date triggerTimeEnd = null;
		if (StringTool.isNotBlank(filterTime)) {
			String[] temp = filterTime.split(" - ");
			if (temp.length == 2) {
				triggerTimeStart = DateUtil.parseDateTime(temp[0]);
				triggerTimeEnd = DateUtil.parseDateTime(temp[1]);
			}
		}

		return datapillarJobLogService.pageList(start, length, jobGroup, jobId, triggerTimeStart, triggerTimeEnd, logStatus);
	}

	@RequestMapping("/get/detailPage")
	public String logDetailPage(HttpServletRequest request, @RequestParam("id") int id, Model model){

		ReturnT<DatapillarJobLog> result = datapillarJobLogService.loadLogDetail(id);
		if (result.getCode() != ReturnT.SUCCESS_CODE) {
			throw new RuntimeException(result.getMsg());
		}

		DatapillarJobLog jobLog = result.getContent();
		model.addAttribute("triggerCode", jobLog.getTriggerCode());
		model.addAttribute("handleCode", jobLog.getHandleCode());
		model.addAttribute("logId", jobLog.getId());
		return "joblog/joblog.detail";
	}

	@RequestMapping("/get/detailContent")
	@ResponseBody
	public ReturnT<LogResult> logDetailCat(@RequestParam("logId") long logId, @RequestParam("fromLineNum") int fromLineNum){
		return datapillarJobLogService.logDetailCat(logId, fromLineNum);
	}

	@RequestMapping("/kill/log")
	@ResponseBody
	public ReturnT<String> logKill(HttpServletRequest request, @RequestParam("id") int id){
		return datapillarJobLogService.logKill(id);
	}

	@RequestMapping("/clear/log")
	@ResponseBody
	public ReturnT<String> clearLog(HttpServletRequest request,
									@RequestParam("jobGroup") int jobGroup,
									@RequestParam("jobId") int jobId,
									@RequestParam("type") int type){
		return datapillarJobLogService.clearLog(jobGroup, jobId, type);
	}

}
