package com.hongmai.common.enums;

import lombok.Getter;

/**
 * 心跳被拒原因。落 t_learn_heartbeat.reject_reason，用于离线审计与防刷取证。
 */
@Getter
public enum HeartbeatRejectReason {

    REPLAY("REPLAY", "重复上报，直接返回首次结果"),
    OUT_OF_ORDER("OUT_OF_ORDER", "序号小于会话内已接受的最大序号"),
    RATE_LIMITED("RATE_LIMITED", "单位时间心跳数超限"),
    OVER_DELTA("OVER_DELTA", "上报间隔超过上限，按上限截断");

    private final String code;
    private final String label;

    HeartbeatRejectReason(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
