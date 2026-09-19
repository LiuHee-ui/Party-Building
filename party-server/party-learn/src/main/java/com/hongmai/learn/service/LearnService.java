package com.hongmai.learn.service;

import com.hongmai.learn.dto.HeartbeatCmd;
import com.hongmai.learn.vo.ReportResultVO;

public interface LearnService {

    /**
     * 上报一次心跳。
     *
     * 同一事务内完成：心跳明细落库 → 会话累计推进 → 学习记录推进 → 学时日累计与流水折算。
     * 任何一步失败整体回滚，避免出现「心跳记了但学时没算」的不一致。
     *
     * 幂等：同一 (sessionId, clientSeq) 重复上报返回首次结果，不重复计入学时。
     *
     * @throws com.hongmai.common.exception.BizException 3001 会话不属于当前用户
     */
    ReportResultVO reportHeartbeat(long userId, HeartbeatCmd cmd);
}
