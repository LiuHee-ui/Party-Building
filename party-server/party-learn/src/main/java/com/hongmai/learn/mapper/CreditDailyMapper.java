package com.hongmai.learn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.learn.domain.CreditDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface CreditDailyMapper extends BaseMapper<CreditDaily> {

    /**
     * 加行锁读取当日累计行。
     *
     * 用 FOR UPDATE 而不是「先查后改」：同一用户同一资源可能有两个心跳并发到达，
     * 不加锁会各自读到相同的旧值，双双通过封顶判断，最终写入超过 120 分钟。
     * 加锁范围仅一行（用户 × 资源 × 日期），竞争极小。
     */
    @Select("""
            SELECT * FROM t_credit_daily
             WHERE user_id = #{userId} AND resource_id = #{resourceId} AND stat_date = #{statDate}
               AND deleted = 0
             FOR UPDATE
            """)
    CreditDaily selectForUpdate(@Param("userId") long userId,
                                @Param("resourceId") long resourceId,
                                @Param("statDate") LocalDate statDate);

    /** 按周期汇总某用户的学时分钟数。 */
    @Select("""
            SELECT COALESCE(SUM(minutes), 0) FROM t_credit_daily
             WHERE user_id = #{userId} AND stat_date BETWEEN #{from} AND #{to} AND deleted = 0
            """)
    int sumMinutesOfPeriod(@Param("userId") long userId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    /**
     * 组织维度的日汇总，供台账与看板使用。
     * 返回列：stat_date / org_id / minutes（去重人数另算）。
     */
    @Select("""
            <script>
            SELECT stat_date AS statDate, org_id AS orgId,
                   SUM(minutes) AS totalMinutes, COUNT(DISTINCT user_id) AS userCount
              FROM t_credit_daily
             WHERE stat_date BETWEEN #{from} AND #{to} AND deleted = 0
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
             GROUP BY stat_date, org_id
             ORDER BY stat_date, org_id
            </script>
            """)
    List<Map<String, Object>> statByOrg(@Param("orgIds") java.util.List<Long> orgIds,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /** 每日活跃人数（当日有效学习分钟数 > 0 的人数），看板活跃度指标用。 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT user_id) FROM t_credit_daily
             WHERE stat_date BETWEEN #{from} AND #{to} AND minutes &gt; 0 AND deleted = 0
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
            </script>
            """)
    long countActiveUsers(@Param("orgIds") java.util.List<Long> orgIds,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    /** 兜底：把已超封顶的历史行修正回 120。用于规则调整后的数据修复。 */
    @Update("""
            UPDATE t_credit_daily SET minutes = 120, updated_at = NOW()
             WHERE minutes > 120 AND deleted = 0
            """)
    int clampOverCapRows();
}
