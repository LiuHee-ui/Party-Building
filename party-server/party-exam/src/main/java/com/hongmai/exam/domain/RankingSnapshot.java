package com.hongmai.exam.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排行榜快照。
 *
 * 为什么需要它：Redis ZSET 只服务「用户端看本组织榜」这个场景，
 * 管理端要跨组织看、要按历史周期回溯，就得有持久化的排行结果。
 * 每日 02:00 定时把 Redis 的分片榜落成快照。
 *
 * 去重键包含 snapshot_date，所以同一天重复跑任务只会覆盖而不会产生重复名次。
 */
@Data
@TableName("t_ranking_snapshot")
public class RankingSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1 个人 / 2 组织 */
    private Integer scopeType;

    /** 1 周 / 2 月 / 3 季 / 4 年 */
    private Integer periodType;

    private String periodKey;

    /** 个人榜为所属党支部；组织榜为被排名的组织 */
    private Long orgId;

    /** 个人榜时有值，组织榜为 0 */
    private Long userId;

    private BigDecimal score;

    private Integer rankNo;

    private LocalDate snapshotDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
