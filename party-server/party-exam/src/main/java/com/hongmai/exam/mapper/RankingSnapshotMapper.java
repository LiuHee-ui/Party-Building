package com.hongmai.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.exam.domain.RankingSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RankingSnapshotMapper extends BaseMapper<RankingSnapshot> {

    /**
     * 取某周期某维度最新的快照日期。
     * 管理端查榜时先取最新快照日，再按该日期取全量名次——
     * 避免快照生成中途被读到「半份」数据。
     */
    @Select("""
            SELECT MAX(snapshot_date) FROM t_ranking_snapshot
             WHERE scope_type = #{scopeType} AND period_type = #{periodType}
               AND period_key = #{periodKey} AND deleted = 0
            """)
    LocalDate selectLatestSnapshotDate(@Param("scopeType") int scopeType,
                                       @Param("periodType") int periodType,
                                       @Param("periodKey") String periodKey);

    /**
     * 按快照日取榜单。
     * orgIds 为 null 表示不限范围，空列表返回空。
     */
    @Select("""
            <script>
            SELECT * FROM t_ranking_snapshot
             WHERE scope_type = #{scopeType} AND period_type = #{periodType}
               AND period_key = #{periodKey} AND snapshot_date = #{snapshotDate} AND deleted = 0
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
             ORDER BY rank_no
             LIMIT #{limit}
            </script>
            """)
    List<RankingSnapshot> selectBySnapshotDate(@Param("scopeType") int scopeType,
                                               @Param("periodType") int periodType,
                                               @Param("periodKey") String periodKey,
                                               @Param("snapshotDate") LocalDate snapshotDate,
                                               @Param("orgIds") List<Long> orgIds,
                                               @Param("limit") int limit);
}
