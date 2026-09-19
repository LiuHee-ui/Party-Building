package com.hongmai.exam.controller;

import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.R;
import com.hongmai.exam.service.ExamService;
import com.hongmai.exam.vo.ExamPaperVO;
import com.hongmai.exam.vo.ExamResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /** 取试卷。返回体不含标准答案。 */
    @GetMapping("/paper/{paperId}")
    public R<ExamPaperVO> paper(@PathVariable long paperId) {
        return R.ok(examService.getPaperForUser(LoginContext.currentUserId(), paperId));
    }

    /**
     * 交卷。请求体为「题目 id → 选中选项」。
     * 客户端不传分数，分数由服务端判定。
     */
    @PostMapping("/paper/{paperId}/submit")
    public R<ExamResultVO> submit(@PathVariable long paperId,
                                  @RequestBody Map<Long, List<String>> answers) {
        return R.ok(examService.submit(LoginContext.currentUserId(), paperId, answers));
    }
}
