package com.hongmai.exam.service;

import com.hongmai.exam.vo.ExamPaperVO;
import com.hongmai.exam.vo.ExamResultVO;

import java.util.List;
import java.util.Map;

public interface ExamService {

    /**
     * 取用户端试卷。返回体中**不含标准答案**。
     * 已提交的试卷返回得分与已提交标记。
     *
     * @throws com.hongmai.common.exception.BizException 4009 试卷不存在或未启用
     */
    ExamPaperVO getPaperForUser(long userId, long paperId);

    /**
     * 交卷。服务端判分 → 落答题记录 → 更新排行榜分值。
     *
     * @throws com.hongmai.common.exception.BizException 4002 该试卷已提交
     */
    ExamResultVO submit(long userId, long paperId, Map<Long, List<String>> answers);
}
