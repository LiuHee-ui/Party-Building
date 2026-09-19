package com.hongmai.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.auth.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按手机号哈希查重。
     * 用哈希而不是密文：GCM 随机 IV 使同一手机号密文每次都不同，等值查询失效。
     * 排除自身 id，供「同一用户重新提交实名」场景使用。
     */
    @Select("""
            SELECT COUNT(1) FROM t_user
             WHERE mobile_hash = #{mobileHash}
               AND deleted = 0
               AND (#{excludeUserId} IS NULL OR id <> #{excludeUserId})
            """)
    int countByMobileHash(@Param("mobileHash") String mobileHash,
                          @Param("excludeUserId") Long excludeUserId);

    /**
     * 按组织范围取待审核实名申请。
     * 数据范围交由调用方通过 orgIds 传入；orgIds 为空列表时返回空。
     */
    @Select("""
            <script>
            SELECT id, real_name, mobile_tail, org_id, identity_type, ethnicity, created_at
              FROM t_user
             WHERE audit_status = 0 AND deleted = 0
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0">
                 AND 1 = 0
               </if>
             ORDER BY created_at DESC
             LIMIT #{offset}, #{limit}
            </script>
            """)
    List<User> selectPendingRealName(@Param("orgIds") List<Long> orgIds,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /** 待审核实名申请总数，与 selectPendingRealName 口径一致。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM t_user
             WHERE audit_status = 0 AND deleted = 0
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0">
                 AND 1 = 0
               </if>
            </script>
            """)
    long countPendingRealName(@Param("orgIds") List<Long> orgIds);

    /** 实名审核状态迁移。带上 audit_status=0 条件，避免重复审核把已通过的用户改回待审。 */
    @Update("""
            UPDATE t_user
               SET audit_status = #{toStatus},
                   audit_remark = #{remark},
                   updated_at = NOW()
             WHERE id = #{userId} AND audit_status = 0 AND deleted = 0
            """)
    int migrateAuditStatus(@Param("userId") long userId,
                           @Param("toStatus") int toStatus,
                           @Param("remark") String remark);

    /**
     * 名册分页查询。只取展示所需字段，不返回手机号密文。
     * 组织范围由调用方传入：orgIds 为 null 表示不限范围，为空列表时退化为恒假条件。
     */
    @Select("""
            <script>
            SELECT id, real_name, mobile_tail, org_id, identity_type, ethnicity, leader_flag
              FROM t_user
             WHERE deleted = 0
               <if test="onlyApproved"> AND audit_status = 1 </if>
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
               <if test="keyword != null and keyword != ''">
                 AND real_name LIKE CONCAT('%', #{keyword}, '%')
               </if>
               <if test="ethnicity != null and ethnicity != ''"> AND ethnicity = #{ethnicity} </if>
               <if test="identityType != null"> AND identity_type = #{identityType} </if>
             ORDER BY id
             LIMIT #{offset}, #{limit}
            </script>
            """)
    List<User> selectRoster(@Param("orgIds") List<Long> orgIds,
                            @Param("keyword") String keyword,
                            @Param("ethnicity") String ethnicity,
                            @Param("identityType") Integer identityType,
                            @Param("onlyApproved") boolean onlyApproved,
                            @Param("offset") int offset,
                            @Param("limit") int limit);

    /** 名册总数，口径必须与 selectRoster 完全一致，否则分页会出现空页或漏数据。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM t_user
             WHERE deleted = 0
               <if test="onlyApproved"> AND audit_status = 1 </if>
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
               <if test="keyword != null and keyword != ''">
                 AND real_name LIKE CONCAT('%', #{keyword}, '%')
               </if>
               <if test="ethnicity != null and ethnicity != ''"> AND ethnicity = #{ethnicity} </if>
               <if test="identityType != null"> AND identity_type = #{identityType} </if>
            </script>
            """)
    long countRoster(@Param("orgIds") List<Long> orgIds,
                     @Param("keyword") String keyword,
                     @Param("ethnicity") String ethnicity,
                     @Param("identityType") Integer identityType,
                     @Param("onlyApproved") boolean onlyApproved);

    /** 批量按 id 取名册展示字段。 */
    @Select("""
            <script>
            SELECT id, real_name, mobile_tail, org_id, identity_type, ethnicity, leader_flag
              FROM t_user
             WHERE deleted = 0 AND id IN
             <foreach collection="ids" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            </script>
            """)
    List<User> selectRosterByIds(@Param("ids") List<Long> ids);

    /**
     * 在册人数。口径与名册、民族统计一致：identity_type IN (1,2,3) 且 audit_status = 1。
     */
    @Select("""
            <script>
            SELECT COUNT(1) FROM t_user
             WHERE deleted = 0 AND audit_status = 1 AND identity_type IN (1, 2, 3)
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
            </script>
            """)
    long countRosterInScope(@Param("orgIds") List<Long> orgIds);

    /**
     * 按民族统计人数。
     * 固定口径：identity_type IN (1,2,3)（党员与积极分子）且 audit_status = 1，
     * 不含群众与学生——这是看板与台账能对上账的前提。
     */
    @Select("""
            <script>
            SELECT ethnicity AS ethnicity, COUNT(1) AS cnt
              FROM t_user
             WHERE deleted = 0
               AND audit_status = 1
               AND identity_type IN (1, 2, 3)
               AND ethnicity IS NOT NULL AND ethnicity &lt;&gt; ''
               <if test="orgIds != null and orgIds.size() > 0">
                 AND org_id IN
                 <foreach collection="orgIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
               <if test="orgIds != null and orgIds.size() == 0"> AND 1 = 0 </if>
             GROUP BY ethnicity
             ORDER BY cnt DESC, ethnicity
            </script>
            """)
    List<java.util.Map<String, Object>> statEthnicity(@Param("orgIds") List<Long> orgIds);
}
