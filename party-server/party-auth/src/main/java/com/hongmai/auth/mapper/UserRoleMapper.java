package com.hongmai.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.auth.domain.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    @Select("SELECT role FROM t_user_role WHERE user_id = #{userId} AND deleted = 0")
    List<Integer> selectRolesByUserId(@Param("userId") long userId);

    @Select("""
            SELECT COUNT(1) FROM t_user_role
             WHERE user_id = #{userId} AND role = #{role} AND deleted = 0
            """)
    int countByUserAndRole(@Param("userId") long userId, @Param("role") int role);
}
