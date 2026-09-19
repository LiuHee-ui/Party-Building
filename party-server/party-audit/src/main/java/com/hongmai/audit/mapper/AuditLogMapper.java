package com.hongmai.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.audit.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 把某业务对象的待审标记清掉。
     * 用一条 UPDATE 处理该 bizId 下所有 pending=1 的流水（理论上只有一条），
     * 避免「先查后改」在并发下漏处理。
     */
    @Update("""
            UPDATE t_audit_log
               SET pending = 0, updated_at = NOW()
             WHERE biz_type = #{bizType}
               AND biz_id = #{bizId}
               AND pending = 1
               AND deleted = 0
            """)
    int clearPending(@Param("bizType") int bizType, @Param("bizId") long bizId);

    /** 统计某业务对象是否仍处于待审状态。 */
    @Select("""
            SELECT COUNT(1) FROM t_audit_log
             WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND pending = 1 AND deleted = 0
            """)
    int countPending(@Param("bizType") int bizType, @Param("bizId") long bizId);
}
