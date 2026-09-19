package com.hongmai.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.content.domain.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    /**
     * 资源状态迁移。带上「原状态」条件，并发重复审核时第二次影响 0 行，
     * 由调用方识别并拒绝——这比先查状态再更新在并发下更可靠。
     */
    @Update("""
            UPDATE t_resource
               SET status = #{toStatus}, updated_at = NOW()
             WHERE id = #{resourceId} AND status = #{fromStatus} AND deleted = 0
            """)
    int migrateStatus(@Param("resourceId") long resourceId,
                      @Param("fromStatus") int fromStatus,
                      @Param("toStatus") int toStatus);
}
