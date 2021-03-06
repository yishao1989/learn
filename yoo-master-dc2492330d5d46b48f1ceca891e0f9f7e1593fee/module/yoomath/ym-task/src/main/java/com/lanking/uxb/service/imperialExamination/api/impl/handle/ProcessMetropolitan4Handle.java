package com.lanking.uxb.service.imperialExamination.api.impl.handle;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lanking.cloud.domain.yoo.activity.imperialExamination.ImperialExaminationActivity;
import com.lanking.cloud.domain.yoo.activity.imperialExamination.ImperialExaminationActivityProcessLog;
import com.lanking.cloud.domain.yoo.activity.imperialExamination.ImperialExaminationExamTag;
import com.lanking.cloud.domain.yoo.activity.imperialExamination.ImperialExaminationProcess;
import com.lanking.cloud.domain.yoo.activity.imperialExamination.ImperialExaminationProcessTime;
import com.lanking.cloud.domain.yoo.activity.imperialExamination.ImperialExaminationType;
import com.lanking.uxb.service.imperialExamination.api.AbstractImperialExaminationProcessHandle;
import com.lanking.uxb.service.imperialExamination.api.TaskActivityHomeworkService;
import com.lanking.uxb.service.imperialExamination.api.TaskImperialExaminationActivityLotteryService;
import com.lanking.uxb.service.imperialExamination.api.TaskImperialExaminationActivityProcessLogService;
import com.lanking.uxb.service.imperialExamination.api.TaskImperialExaminationActivityRankStatService;
import com.lanking.uxb.service.imperialExamination.api.TaskImperialExaminationActivityService;

import httl.util.CollectionUtils;

/**
 * 会试批改下发阶段
 * 定时查询老师和学生的会试成绩并排名，这个阶段只会有首次考试的成绩
 * */
@Component
public class ProcessMetropolitan4Handle extends AbstractImperialExaminationProcessHandle {

	@Autowired
	private TaskImperialExaminationActivityRankStatService imperialExaminationActivityRankStatService;
	@Autowired
	private TaskImperialExaminationActivityProcessLogService activityProcessLogService;
	@Autowired
	private TaskActivityHomeworkService homeworkService;
	@Autowired
	private TaskImperialExaminationActivityLotteryService lotteryService;
	@Autowired
	private TaskImperialExaminationActivityService activityService;

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Override
	public ImperialExaminationProcess getProcess() {
		return ImperialExaminationProcess.PROCESS_METROPOLITAN4;
	}

	@Override
	public boolean isLotteryProcessed(long code) {
		ImperialExaminationActivityProcessLog log = activityProcessLogService.get(code, getProcess(),
				METROPOLITAN_LOTTERY_PROCESSED);
		if (log != null) {
			return true;
		}

		return false;
	}

	@Override
	public synchronized void process(long code) {
		//不是当前阶段，不处理
		if(!isCurrentProcess(code)){
			return;
		}

		if (!isLotteryProcessed(code)) {
			ImperialExaminationActivityProcessLog activityLog = new ImperialExaminationActivityProcessLog();
			activityLog.setActivityCode(code);
			activityLog.setProcess(getProcess());
			activityLog.setStartTime(new Date());
			// 获取活动
			ImperialExaminationActivity activity = activityService.get(code);
			List<ImperialExaminationProcessTime> timeList = activity.getCfg().getTimeList();

			// 1.统计所有imperial_exam_homework表中规定时间内已下发的作业
			Date issueStartTime = null;
			Date issueEndTime = null;
			
			for (ImperialExaminationProcessTime ptime : timeList) {
				// 答题开始时间
				if (ptime.getProcess() == ImperialExaminationProcess.PROCESS_METROPOLITAN2) {
					issueStartTime = ptime.getStartTime();
					break;
				}
				// 批改下发时间
				if (ptime.getProcess() == ImperialExaminationProcess.PROCESS_METROPOLITAN3) {
					issueEndTime = ptime.getEndTime();
					break;
				}
			}
			// 给老师发奖券
			List<Long> userIds = homeworkService.getIssueHomeworkUserId(code, issueStartTime, issueEndTime,
					ImperialExaminationProcess.PROCESS_METROPOLITAN5);

			if (CollectionUtils.isNotEmpty(userIds)) {
				for (Long id : userIds) {
					lotteryService.addLottery(id, ImperialExaminationProcess.PROCESS_METROPOLITAN5, code);
				}
			}

			
			// 给学生发奖券,条件是学生在规定的时间内下发作业并且正确率不为0
			List<Long> studentIds = homeworkService.getIssuedHomeworkStudentId(code, issueStartTime,
					issueEndTime);

			if (CollectionUtils.isNotEmpty(studentIds)) {
				for (Long id : studentIds) {
					lotteryService.addLottery(id, ImperialExaminationProcess.PROCESS_METROPOLITAN5, code);
				}
			}

			activityLog.setProcessData(METROPOLITAN_LOTTERY_PROCESSED);
			activityLog.setSuccess(true);
			activityLog.setEndTime(new Date());
			// 更新日志
			activityProcessLogService.create(activityLog);
		}

		ImperialExaminationActivityProcessLog activityLog = new ImperialExaminationActivityProcessLog();
		activityLog.setActivityCode(code);
		activityLog.setProcess(getProcess());
		activityLog.setStartTime(new Date());

		try {
			//这个阶段只需要生成首次考试的成绩
			imperialExaminationActivityRankStatService.countRank(code, ImperialExaminationType.METROPOLITAN_EXAMINATION,
							ImperialExaminationExamTag.FIRST_EXAM.getValue(),ImperialExaminationExamTag.FIRST_EXAM.getValue());
			activityLog.setSuccess(true);
		} catch (Exception e) {
			logger.error("PROCESS_METROPOLITAN4 fail:"+e);
			activityLog.setSuccess(false);
		}
		activityLog.setProcessData(PROCESS_SCORE + getProcess());
		activityLog.setEndTime(new Date());
		// 更新日志
		activityProcessLogService.create(activityLog);
	}

}
