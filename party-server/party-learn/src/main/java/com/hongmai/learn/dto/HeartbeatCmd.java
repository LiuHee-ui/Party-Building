package com.hongmai.learn.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 学习心跳上报入参。 */
@Data
public class HeartbeatCmd {

    /** 会话标识，客户端进入播放页时生成；重复播放同一资源会产生新会话 */
    @NotBlank(message = "会话标识不能为空")
    private String sessionId;

    @NotNull(message = "资源不能为空")
    private Long resourceId;

    /** 会话内自增序号，从 1 起。服务端靠它做幂等与乱序判定 */
    @NotNull(message = "序号不能为空")
    @Min(value = 1, message = "序号从 1 起")
    private Integer clientSeq;

    /** 距上次上报的秒数 */
    @NotNull(message = "间隔不能为空")
    private Integer deltaSec;

    /** 本次间隔内页面处于前台的秒数 */
    @NotNull(message = "聚焦时长不能为空")
    private Integer focusedSec;

    /** 播放位置（秒），用于续播 */
    private Integer positionSec;

    /** 资源总时长（秒），用于计算完成度；未知传 0 */
    private Integer durationSec;
}
