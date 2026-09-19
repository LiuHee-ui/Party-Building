package com.hongmai.learn.vo;

import lombok.Data;

/** 心跳上报结果。客户端据此展示学时与进度，不需要自己计算。 */
@Data
public class ReportResultVO {

    /** 本次心跳是否被接受（重复上报会返回 false，但 acceptedSec 是首次的结果） */
    private boolean accepted;

    /** 本次实际计入的有效秒数 */
    private int acceptedSec;

    /** 被拒原因，accepted=true 时可能为 OVER_DELTA（截断） */
    private String rejectReason;

    /** 本会话累计有效秒数 */
    private int sessionValidSec;

    /** 当日该资源已计入分钟数（已封顶） */
    private int todayMinutes;

    /** 当日该资源是否已触及 120 分钟封顶 */
    private boolean dailyCapReached;

    /** 该资源当日折算的学时 */
    private java.math.BigDecimal todayCredit;

    /** 完成度百分比 */
    private java.math.BigDecimal progressPct;
}
