package com.hongmai.learn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongmai.learn.domain.CreditTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CreditTargetMapper extends BaseMapper<CreditTarget> {

    /** 取个人设置的目标学时。返回 null 表示未设置，调用方回落到默认规则。 */
    @Select("""
            SELECT * FROM t_credit_target
             WHERE user_id = #{userId} AND period_type = #{periodType} AND period_key = #{periodKey}
               AND deleted = 0
            """)
    CreditTarget selectByUserPeriod(@Param("userId") long userId,
                                    @Param("periodType") int periodType,
                                    @Param("periodKey") String periodKey);
}
