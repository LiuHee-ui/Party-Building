package com.hongmai.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.auth.domain.AdminApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminApplicationMapper extends BaseMapper<AdminApplication> {

    /**
     * 状态迁移。带上 status=0 条件，保证「只有待审核的申请能被审核」，
     * 并发重复审核时第二次会返回 0 行，由调用方识别并拒绝。
     */
    @Update("""
            UPDATE t_admin_application
               SET status = #{toStatus},
                   reviewer_id = #{reviewerId},
                   reviewed_at = NOW(),
                   remark = #{remark},
                   updated_at = NOW()
             WHERE id = #{applyId} AND status = 0 AND deleted = 0
            """)
    int migrateStatus(@Param("applyId") long applyId,
                      @Param("toStatus") int toStatus,
                      @Param("reviewerId") long reviewerId,
                      @Param("remark") String remark);
}
