package com.hongmai.learn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.learn.domain.Credit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface CreditMapper extends BaseMapper<Credit> {

    /** 某用户在某周期已入账学时合计。 */
    @Select("""
            SELECT COALESCE(SUM(credit), 0) FROM t_credit
             WHERE user_id = #{userId} AND period_key = #{periodKey} AND deleted = 0
            """)
    BigDecimal sumCredit(@Param("userId") long userId, @Param("periodKey") String periodKey);

    /** 批量取多人在某周期的学时，供名册与排行榜使用。 */
    @Select("""
            <script>
            SELECT user_id AS userId, COALESCE(SUM(credit), 0) AS credit
              FROM t_credit
             WHERE period_key = #{periodKey} AND deleted = 0
               AND user_id IN
               <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
             GROUP BY user_id
            </script>
            """)
    List<Map<String, Object>> sumCreditByUsers(@Param("userIds") List<Long> userIds,
                                               @Param("periodKey") String periodKey);

    /**
     * 组织维度的学时台账。
     * 按人聚合后 LEFT JOIN 不在这里做——用户信息属 party-auth，
     * 调用方拿到 userId 与学时后再通过 UserRosterProvider 回填姓名等字段。
     */
    @Select("""
            <script>
            SELECT user_id AS userId, org_id AS orgId,
                   COALESCE(SUM(minutes), 0) AS totalMinutes,
                   COALESCE(SUM(credit), 0) AS totalCredit
              FROM t_credit
             WHERE period_key = #{periodKey} AND period_type = #{periodType} AND deleted = 0
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
             GROUP BY user_id, org_id
             ORDER BY totalCredit DESC
            </script>
            """)
    List<Map<String, Object>> statOrgLedger(@Param("orgIds") List<Long> orgIds,
                                            @Param("periodType") int periodType,
                                            @Param("periodKey") String periodKey);

    /** 某周期内是否有入账记录（判断是否已折算），配合唯一键做幂等前置检查。 */
    @Select("""
            SELECT COUNT(1) FROM t_credit
             WHERE user_id = #{userId} AND period_type = #{periodType} AND period_key = #{periodKey}
               AND source_type = #{sourceType} AND source_id = #{sourceId} AND deleted = 0
            """)
    int countSource(@Param("userId") long userId,
                    @Param("periodType") int periodType,
                    @Param("periodKey") String periodKey,
                    @Param("sourceType") int sourceType,
                    @Param("sourceId") long sourceId);

    /**
     * 按日期区间统计每人的学时，供排行榜快照任务使用。
     * 用 occurred_on 而不是 period_key：周榜/月榜/季榜的区间边界靠日期判断，
     * period_key 只区分自然年与五年期，表达不了「本周」。
     */
    @Select("""
            <script>
            SELECT user_id AS userId, org_id AS orgId, COALESCE(SUM(credit), 0) AS credit
              FROM t_credit
             WHERE deleted = 0 AND period_type = #{periodType}
               AND occurred_on BETWEEN #{from} AND #{to}
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
             GROUP BY user_id, org_id
            </script>
            """)
    List<Map<String, Object>> statUserCreditInRange(@Param("orgIds") List<Long> orgIds,
                                                    @Param("periodType") int periodType,
                                                    @Param("from") java.time.LocalDate from,
                                                    @Param("to") java.time.LocalDate to);

    /**
     * 按日累计流水写入/更新。
     *
     * 用 INSERT ... ON DUPLICATE KEY UPDATE 而不是「先查后写」：
     * uk_credit_source 保证同一来源在同一周期只有一条分录，
     * 重复调用只会把 minutes 刷新成最新的当日累计值，不会重复入账。
     * 每日多次心跳都会走这里，所以必须幂等。
     */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO t_credit
                (user_id, org_id, period_type, period_key, source_type, source_id,
                 minutes, credit, occurred_on, created_at, updated_at, deleted)
            VALUES
                (#{userId}, #{orgId}, #{periodType}, #{periodKey}, #{sourceType}, #{sourceId},
                 #{minutes}, #{credit}, #{occurredOn}, NOW(), NOW(), 0)
            ON DUPLICATE KEY UPDATE
                minutes = VALUES(minutes),
                credit = VALUES(credit),
                org_id = VALUES(org_id),
                updated_at = NOW()
            """)
    int upsertDailyLedger(@Param("userId") long userId,
                          @Param("orgId") long orgId,
                          @Param("periodType") int periodType,
                          @Param("periodKey") String periodKey,
                          @Param("sourceType") int sourceType,
                          @Param("sourceId") long sourceId,
                          @Param("minutes") int minutes,
                          @Param("credit") BigDecimal credit,
                          @Param("occurredOn") java.time.LocalDate occurredOn);
}
