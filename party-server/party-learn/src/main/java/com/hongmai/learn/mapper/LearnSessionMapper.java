package com.hongmai.learn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.learn.domain.LearnSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface LearnSessionMapper extends BaseMapper<LearnSession> {

    @Select("SELECT * FROM t_learn_session WHERE session_id = #{sessionId} AND deleted = 0")
    LearnSession selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 推进会话累计。
     * 带 last_seq < #{clientSeq} 条件：并发两个心跳同时到达时，
     * 只有序号更大的那个能推进，避免累计被较小序号的请求覆盖。
     */
    @Update("""
            UPDATE t_learn_session
               SET last_seq = #{clientSeq},
                   last_end_at = #{lastEndAt},
                   valid_sec = valid_sec + #{addValidSec},
                   unfocused_sec = unfocused_sec + #{addUnfocusedSec},
                   updated_at = NOW()
             WHERE session_id = #{sessionId}
               AND last_seq < #{clientSeq}
               AND deleted = 0
            """)
    int advance(@Param("sessionId") String sessionId,
                @Param("clientSeq") int clientSeq,
                @Param("lastEndAt") LocalDateTime lastEndAt,
                @Param("addValidSec") int addValidSec,
                @Param("addUnfocusedSec") int addUnfocusedSec);
}
