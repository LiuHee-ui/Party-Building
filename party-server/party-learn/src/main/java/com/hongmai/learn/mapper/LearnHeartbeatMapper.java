package com.hongmai.learn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.learn.domain.LearnHeartbeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LearnHeartbeatMapper extends BaseMapper<LearnHeartbeat> {

    /**
     * 查询某个 (session, seq) 是否已处理过。
     * 表上有 uk_session_seq 唯一约束，这里是「先查」的快捷路径；
     * 真正的幂等保障是那个唯一键——并发下靠它兜底。
     */
    @Select("""
            SELECT accepted_sec FROM t_learn_heartbeat
             WHERE session_id = #{sessionId} AND client_seq = #{clientSeq} AND deleted = 0
            """)
    Integer selectAcceptedSec(@Param("sessionId") String sessionId,
                              @Param("clientSeq") int clientSeq);

    /** 某会话已记录的心跳条数，用于排障与限流观测。 */
    @Select("SELECT COUNT(1) FROM t_learn_heartbeat WHERE session_id = #{sessionId} AND deleted = 0")
    int countBySession(@Param("sessionId") String sessionId);
}
