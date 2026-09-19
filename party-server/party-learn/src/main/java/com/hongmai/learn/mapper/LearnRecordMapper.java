package com.hongmai.learn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.learn.domain.LearnRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface LearnRecordMapper extends BaseMapper<LearnRecord> {

    @Select("""
            SELECT * FROM t_learn_record
             WHERE user_id = #{userId} AND resource_id = #{resourceId} AND deleted = 0
            """)
    LearnRecord selectByUserResource(@Param("userId") long userId,
                                     @Param("resourceId") long resourceId);

    /**
     * 推进学习记录。
     * progress_pct 只在变大时才更新（GREATEST），避免用户回看前面一段就把进度改小。
     */
    @Update("""
            UPDATE t_learn_record
               SET valid_sec = valid_sec + #{addValidSec},
                   last_position_sec = #{positionSec},
                   progress_pct = GREATEST(progress_pct, #{progressPct}),
                   last_learn_at = #{lastLearnAt},
                   unread_flag = 0,
                   updated_at = NOW()
             WHERE user_id = #{userId} AND resource_id = #{resourceId} AND deleted = 0
            """)
    int advance(@Param("userId") long userId,
                @Param("resourceId") long resourceId,
                @Param("addValidSec") int addValidSec,
                @Param("positionSec") int positionSec,
                @Param("progressPct") java.math.BigDecimal progressPct,
                @Param("lastLearnAt") LocalDateTime lastLearnAt);
}
