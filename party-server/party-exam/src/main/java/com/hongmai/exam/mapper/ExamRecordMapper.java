package com.hongmai.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.exam.domain.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    @Select("""
            SELECT * FROM t_exam_record
             WHERE user_id = #{userId} AND paper_id = #{paperId} AND deleted = 0
            """)
    ExamRecord selectByUserPaper(@Param("userId") long userId, @Param("paperId") long paperId);

    /** 平均分与参试人数。无记录时返回 null，由调用方处理。 */
    @Select("""
            <script>
            SELECT AVG(score) AS avgScore, COUNT(DISTINCT user_id) AS userCount
              FROM t_exam_record
             WHERE deleted = 0 AND submitted_at BETWEEN #{from} AND #{to}
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
            </script>
            """)
    Map<String, Object> statAvgScore(@Param("orgIds") List<Long> orgIds,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /**
     * 成绩分布。分桶在 SQL 里做，与看板口径一致——
     * 在 Java 里分桶需要把全部记录捞回来，数据量一大就会成为性能瓶颈。
     * 每个用户取最高分（一人多次测验只计最好成绩）。
     */
    @Select("""
            <script>
            SELECT bucket, COUNT(1) AS cnt FROM (
              SELECT CASE
                       WHEN best_score &lt; 60 THEN 'A'
                       WHEN best_score &lt; 70 THEN 'B'
                       WHEN best_score &lt; 80 THEN 'C'
                       WHEN best_score &lt; 90 THEN 'D'
                       ELSE 'E' END AS bucket
                FROM (
                  SELECT MAX(score) AS best_score
                    FROM t_exam_record
                   WHERE deleted = 0 AND submitted_at BETWEEN #{from} AND #{to}
                     <if test="orgIds != null and orgIds.size() > 0">
                       AND org_id IN
                       <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
                     </if>
                     <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
                   GROUP BY user_id
                ) per_user
            ) buckets
            GROUP BY bucket
            ORDER BY bucket
            </script>
            """)
    List<Map<String, Object>> statScoreBuckets(@Param("orgIds") List<Long> orgIds,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /** 取某周期内每人的最高分，用于「答题得分」排行榜。 */
    @Select("""
            <script>
            SELECT user_id AS userId, org_id AS orgId, MAX(score) AS bestScore
              FROM t_exam_record
             WHERE deleted = 0 AND submitted_at BETWEEN #{from} AND #{to}
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
             GROUP BY user_id, org_id
            </script>
            """)
    List<Map<String, Object>> statBestScorePerUser(@Param("orgIds") List<Long> orgIds,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    /** 某人某周期的最高分。 */
    @Select("""
            SELECT COALESCE(MAX(score), 0) FROM t_exam_record
             WHERE user_id = #{userId} AND deleted = 0 AND submitted_at BETWEEN #{from} AND #{to}
            """)
    BigDecimal maxScoreOfPeriod(@Param("userId") long userId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}
