package com.hongmai.exam.service.impl;

import com.hongmai.common.enums.PeriodType;
import com.hongmai.common.enums.RankingMetric;
import com.hongmai.common.enums.RankingPeriodType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.util.DateUtil;
import com.hongmai.common.web.LoginContext;
import com.hongmai.exam.domain.ExamJudge;
import com.hongmai.exam.domain.ExamPaper;
import com.hongmai.exam.domain.ExamRecord;
import com.hongmai.exam.domain.PaperQuestion;
import com.hongmai.exam.domain.PaperQuestionsCodec;
import com.hongmai.exam.mapper.ExamPaperMapper;
import com.hongmai.exam.mapper.ExamRecordMapper;
import com.hongmai.exam.service.ExamService;
import com.hongmai.exam.service.RankingService;
import com.hongmai.exam.vo.ExamPaperVO;
import com.hongmai.exam.vo.ExamResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    /** 答题榜按周统计，周期结束自动换榜，激励效果比年度榜明显 */
    private static final int EXAM_RANK_PERIOD_TYPE = RankingPeriodType.WEEK.getCode();

    private final ExamPaperMapper paperMapper;
    private final ExamRecordMapper recordMapper;
    private final RankingService rankingService;

    @Override
    public ExamPaperVO getPaperForUser(long userId, long paperId) {
        ExamPaper paper = requireEnabledPaper(paperId);
        List<PaperQuestion> questions = PaperQuestionsCodec.decode(paper.getQuestionIdsJson());

        ExamPaperVO vo = new ExamPaperVO();
        vo.setPaperId(paper.getId());
        vo.setTitle(paper.getTitle());
        vo.setTotalScore(paper.getTotalScore());

        ExamRecord record = recordMapper.selectByUserPaper(userId, paperId);
        vo.setSubmitted(record != null);
        vo.setScore(record == null ? null : record.getScore());

        // 只装配题干与选项。答题前不回显答案，也不回显对错
        vo.setQuestions(questions.stream().map(this::toQuestionItem).toList());
        return vo;
    }

    private ExamPaperVO.QuestionItem toQuestionItem(PaperQuestion question) {
        ExamPaperVO.QuestionItem item = new ExamPaperVO.QuestionItem();
        item.setId(question.id());
        item.setType(question.type());
        item.setTypeLabel(typeLabel(question.type()));
        item.setScore(question.score());
        item.setStem(question.stem());
        item.setOptions(question.options() == null ? List.of() : question.options().stream()
                .map(option -> {
                    ExamPaperVO.OptionItem optionItem = new ExamPaperVO.OptionItem();
                    optionItem.setKey(option.key());
                    optionItem.setText(option.text());
                    return optionItem;
                })
                .toList());
        return item;
    }

    private String typeLabel(int type) {
        return switch (type) {
            case ExamJudge.TYPE_SINGLE -> "单选";
            case ExamJudge.TYPE_MULTI -> "多选";
            case ExamJudge.TYPE_TRUE_FALSE -> "判断";
            default -> "未知";
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResultVO submit(long userId, long paperId, Map<Long, List<String>> answers) {
        ExamPaper paper = requireEnabledPaper(paperId);
        List<PaperQuestion> questions = PaperQuestionsCodec.decode(paper.getQuestionIdsJson());

        // 判分全部在服务端完成，客户端只提交「选了哪些选项」
        ExamJudge.JudgeResult judgeResult = ExamJudge.judge(
                questions.stream().map(PaperQuestion::toJudgingQuestion).toList(), answers);

        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setPaperId(paperId);
        record.setOrgId(currentOrgId());
        record.setScore(judgeResult.score());
        record.setCorrectCount(judgeResult.correctCount());
        record.setTotalCount(judgeResult.totalCount());
        record.setDetailJson(encodeDetails(judgeResult));
        record.setSubmittedAt(DateUtil.now());

        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // uk_user_paper 是「一份试卷只能提交一次」的最终保障。
            // 靠唯一键而不是「先查后插」，并发重复提交才能被真正挡住
            throw new BizException(ErrorCode.EXAM_DUPLICATE_SUBMIT);
        }

        String periodKey = DateUtil.rankingPeriodKey(DateUtil.today(), EXAM_RANK_PERIOD_TYPE);
        rankingService.keepMaxScore(record.getOrgId(), userId, RankingMetric.EXAM_SCORE,
                EXAM_RANK_PERIOD_TYPE, periodKey, judgeResult.score().doubleValue());

        ExamResultVO vo = new ExamResultVO();
        vo.setRecordId(record.getId());
        vo.setPaperId(paperId);
        vo.setScore(judgeResult.score());
        vo.setCorrectCount(judgeResult.correctCount());
        vo.setTotalCount(judgeResult.totalCount());
        vo.setPassed(ExamJudge.isPassed(judgeResult.score()));
        // 交卷后才回显标准答案，供错题回顾
        vo.setDetails(judgeResult.details().stream().map(detail -> {
            ExamResultVO.QuestionResult questionResult = new ExamResultVO.QuestionResult();
            questionResult.setQuestionId(detail.questionId());
            questionResult.setType(detail.type());
            questionResult.setScore(detail.score());
            questionResult.setCorrect(detail.correct());
            questionResult.setSelected(detail.selected());
            questionResult.setCorrectOptions(detail.correctOptions());
            return questionResult;
        }).toList());
        vo.setRankInOrg(rankingService.rankOf(record.getOrgId(), userId,
                RankingMetric.EXAM_SCORE, EXAM_RANK_PERIOD_TYPE, periodKey));
        return vo;
    }

    private String encodeDetails(ExamJudge.JudgeResult result) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(result.details());
        } catch (Exception e) {
            log.error("判分明细序列化失败", e);
            return "[]";
        }
    }

    private ExamPaper requireEnabledPaper(long paperId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null || !paper.isEnabled()) {
            throw new BizException(ErrorCode.PAPER_NOT_FOUND);
        }
        return paper;
    }

    private long currentOrgId() {
        LoginContext context = LoginContext.get();
        return context == null ? 0L : context.getPrimaryOrgId();
    }
}
