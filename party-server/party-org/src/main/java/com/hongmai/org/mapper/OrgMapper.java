package com.hongmai.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.org.domain.Org;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrgMapper extends BaseMapper<Org> {

    /**
     * 取某路径子树下的全部启用组织 id（含自身）。
     * 这是数据范围查询的唯一实现方式——一次索引扫描替代递归查询。
     */
    @Select("""
            SELECT id FROM t_org
             WHERE path LIKE CONCAT(#{path}, '%') AND status = 1 AND deleted = 0
            """)
    List<Long> selectIdsUnderPath(@Param("path") String path);

    /** 取组织层级，供 OrgLevelProvider 使用。不存在或已停用返回 null。 */
    @Select("""
            SELECT level FROM t_org
             WHERE id = #{orgId} AND status = 1 AND deleted = 0
            """)
    Integer selectLevel(@Param("orgId") long orgId);

    /** 取组织名称，供名册回填使用。 */
    @Select("""
            <script>
            SELECT id, name FROM t_org
             WHERE deleted = 0 AND id IN
             <foreach collection="ids" item="oid" open="(" separator="," close=")">#{oid}</foreach>
            </script>
            """)
    List<Org> selectNamesByIds(@Param("ids") List<Long> ids);
}
