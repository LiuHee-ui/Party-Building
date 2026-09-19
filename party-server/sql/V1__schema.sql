-- =============================================================================
-- 红脉云筑 · 智慧党建小程序 —— V1 建表脚本
-- 依据：docs/plan.md §4 核心数据结构
-- 约定：所有时间以 Asia/Shanghai 存储；枚举以 TINYINT 存库；
--       所有表含 id / created_at / updated_at / deleted（逻辑删除）
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `hongmai_party`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hongmai_party`;

-- -----------------------------------------------------------------------------
-- 1. t_org 组织
-- 不变式：path 必须以 /{id}/ 结尾；组织移动时须整棵子树重写 path
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_org`;
CREATE TABLE `t_org`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL COMMENT '组织名称',
    `level`       TINYINT      NOT NULL COMMENT '1 党委 / 2 党总支 / 3 党支部',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级组织，根为 0',
    `path`        VARCHAR(255) NOT NULL COMMENT '祖先路径 /1/12/35/，数据范围查询用',
    `region_code` VARCHAR(12)           DEFAULT NULL COMMENT '行政区划代码',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 停用',
    `created_at`  DATETIME     NOT NULL,
    `updated_at`  DATETIME     NOT NULL,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_path` (`path`),
    KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB COMMENT ='组织';

-- -----------------------------------------------------------------------------
-- 2. t_user 用户与党员
-- 不变式：audit_status != 1 的用户不计入台账、不参与排行与考核
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user`
(
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `open_id`       VARCHAR(64)   NOT NULL COMMENT '微信 openid',
    `real_name`     VARCHAR(50)            DEFAULT NULL COMMENT '实名姓名，未实名时为空',
    `mobile_cipher` VARBINARY(128)         DEFAULT NULL COMMENT '手机号密文（N2）',
    `mobile_hash`   VARCHAR(64)            DEFAULT NULL COMMENT '手机号 SHA-256 哈希，仅用于唯一性校验',
    `mobile_tail`   VARCHAR(4)             DEFAULT NULL COMMENT '手机号末四位，列表直接展示',
    `org_id`        BIGINT                 DEFAULT NULL COMMENT '所属组织，未实名时为空',
    `identity_type` TINYINT                DEFAULT NULL COMMENT '1 正式党员/2 预备党员/3 积极分子/4 群众/5 学生',
    `ethnicity`     VARCHAR(20)            DEFAULT NULL COMMENT '民族',
    `leader_flag`   TINYINT       NOT NULL DEFAULT 0 COMMENT '1=书记或班子成员，决定默认目标学时',
    `audit_status`  TINYINT       NOT NULL DEFAULT 9 COMMENT '9 未提交实名 / 0 待审核 / 1 通过 / 2 驳回',
    `audit_remark`  VARCHAR(255)           DEFAULT NULL COMMENT '审核意见',
    `avatar_url`    VARCHAR(255)           DEFAULT NULL COMMENT '头像',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 停用',
    `created_at`    DATETIME      NOT NULL,
    `updated_at`    DATETIME      NOT NULL,
    `deleted`       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_open_id` (`open_id`),
    UNIQUE KEY `uk_mobile_hash` (`mobile_hash`),
    KEY `idx_org_ethnicity` (`org_id`, `ethnicity`),
    KEY `idx_org_audit` (`org_id`, `audit_status`)
) ENGINE = InnoDB COMMENT ='用户与党员';

-- -----------------------------------------------------------------------------
-- 3. t_user_role 用户角色（多角色存多行）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_user_role`;
CREATE TABLE `t_user_role`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '用户',
    `role`       TINYINT  NOT NULL COMMENT '1 普通用户/2 积极分子/3 组织管理员/4 上级组织管理员/5 平台管理员',
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `deleted`    TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role`)
) ENGINE = InnoDB COMMENT ='用户角色';

-- -----------------------------------------------------------------------------
-- 4. t_resource 学习资源
-- 不变式：form=4 时 vr_theme/vr_form 必填且 content_url 必空；
--        form∈{2,3} 时 subtitle_url 必填；form∈{1,4} 时 subtitle_url 必空
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_resource`;
CREATE TABLE `t_resource`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(200) NOT NULL COMMENT '标题',
    `summary`       VARCHAR(500)          DEFAULT NULL COMMENT '简介',
    `cover_url`     VARCHAR(500)          DEFAULT NULL COMMENT '封面',
    `form`          TINYINT      NOT NULL COMMENT '1 图文/2 视频/3 全景/4 VR',
    `theme`         TINYINT      NOT NULL COMMENT '1 党的历史/2 理论知识/3 先进事迹',
    `vr_theme`      TINYINT               DEFAULT NULL COMMENT 'VR 主题 1-4，仅 form=4',
    `vr_form`       TINYINT               DEFAULT NULL COMMENT 'VR 形态 1-3，仅 form=4',
    `content_url`   VARCHAR(500)          DEFAULT NULL COMMENT '内容地址，form=4 时必须为空',
    `duration_sec`  INT                   DEFAULT NULL COMMENT '时长（秒），form=1 可空',
    `subtitle_url`  VARCHAR(500)          DEFAULT NULL COMMENT '中文字幕地址（N6/AC28），form∈{2,3} 必填',
    `subtitle_lang` VARCHAR(16)  NOT NULL DEFAULT 'zh-Hans' COMMENT '字幕语言，固定中文，不随界面语言切换',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 草稿/1 待审核/2 已上架/3 已下架',
    `sort_no`       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `created_by`    BIGINT       NOT NULL COMMENT '创建人',
    `created_at`    DATETIME     NOT NULL,
    `updated_at`    DATETIME     NOT NULL,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_status_form_theme` (`status`, `form`, `theme`)
) ENGINE = InnoDB COMMENT ='学习资源';

-- -----------------------------------------------------------------------------
-- 5. t_learn_session 学习时段（会话行，序号单调）
-- 序号空间统一为「会话内」，用户同日重进同一资源会产生新会话，序号从 1 重新计数
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_learn_session`;
CREATE TABLE `t_learn_session`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `session_id`    VARCHAR(64) NOT NULL COMMENT '客户端会话标识',
    `user_id`       BIGINT      NOT NULL,
    `resource_id`   BIGINT      NOT NULL,
    `org_id`        BIGINT      NOT NULL COMMENT '冗余，看板按组织聚合',
    `begin_at`      DATETIME    NOT NULL COMMENT '时段开始',
    -- 毫秒精度：这个字段用于计算两次心跳的间隔（限流阈值 3 秒）。
    -- 若用秒精度的 DATETIME，MySQL 会把小数秒四舍五入，真实的 3.2 秒间隔
    -- 可能被算成 2.9 秒而误判限流 —— 判定精度不能超过存储精度。
    `last_end_at`   DATETIME(3)          DEFAULT NULL COMMENT '最后一次被接受的心跳时刻；会话刚建立、尚未接受过心跳时为空',
    `last_seq`      INT         NOT NULL DEFAULT 0 COMMENT '本会话已接受的最大 client_seq',
    `unfocused_sec` INT         NOT NULL DEFAULT 0 COMMENT '本会话累计失焦秒数',
    `valid_sec`     INT         NOT NULL DEFAULT 0 COMMENT '本会话累计有效秒数',
    `source`        TINYINT     NOT NULL DEFAULT 1 COMMENT '1 在线 / 2 断网补传',
    `reported_at`   DATETIME    NOT NULL,
    `created_at`    DATETIME    NOT NULL,
    `updated_at`    DATETIME    NOT NULL,
    `deleted`       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_resource` (`user_id`, `resource_id`)
) ENGINE = InnoDB COMMENT ='学习时段（会话）';

-- -----------------------------------------------------------------------------
-- 6. t_learn_heartbeat 心跳明细（幂等与审计）
-- uk_session_seq 是断网补传重复上报的幂等键
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_learn_heartbeat`;
CREATE TABLE `t_learn_heartbeat`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `session_id`    VARCHAR(64) NOT NULL,
    `client_seq`    INT         NOT NULL COMMENT '会话内自增序号，从 1 起',
    `delta_sec`     INT         NOT NULL COMMENT '上报间隔秒数',
    `focused_sec`   INT         NOT NULL COMMENT '上报的聚焦秒数',
    `position_sec`  INT         NOT NULL COMMENT '播放位置',
    `accepted_sec`  INT         NOT NULL DEFAULT 0 COMMENT '服务端认定计入的有效秒数',
    `reject_reason` VARCHAR(32)          DEFAULT NULL COMMENT 'REPLAY/OUT_OF_ORDER/RATE_LIMITED/OVER_DELTA',
    `reported_at`   DATETIME    NOT NULL,
    `created_at`    DATETIME    NOT NULL,
    `updated_at`    DATETIME    NOT NULL,
    `deleted`       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_seq` (`session_id`, `client_seq`)
) ENGINE = InnoDB COMMENT ='学习心跳明细';

-- -----------------------------------------------------------------------------
-- 7. t_learn_record 学习记录（用户 × 资源聚合）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_learn_record`;
CREATE TABLE `t_learn_record`
(
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT        NOT NULL,
    `resource_id`       BIGINT        NOT NULL,
    `org_id`            BIGINT        NOT NULL COMMENT '冗余',
    `last_position_sec` INT           NOT NULL DEFAULT 0 COMMENT '上次进度位置，续播用',
    `progress_pct`      DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '完成百分比 0-100',
    `valid_sec`         INT           NOT NULL DEFAULT 0 COMMENT '累计有效学习秒数',
    `unread_flag`       TINYINT       NOT NULL DEFAULT 1 COMMENT '1 未开始 / 0 已开始',
    `first_start_at`    DATETIME               DEFAULT NULL,
    `last_learn_at`     DATETIME               DEFAULT NULL,
    `finish_at`         DATETIME               DEFAULT NULL,
    `created_at`        DATETIME      NOT NULL,
    `updated_at`        DATETIME      NOT NULL,
    `deleted`           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`),
    KEY `idx_org_last_learn` (`org_id`, `last_learn_at`)
) ENGINE = InnoDB COMMENT ='学习记录';

-- -----------------------------------------------------------------------------
-- 8. t_credit_daily 学时日累计
-- 不变式：0 <= minutes <= 120，承载 spec F8「单条资源单日最多计 2 学时」
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_credit_daily`;
CREATE TABLE `t_credit_daily`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL,
    `resource_id` BIGINT   NOT NULL,
    `org_id`      BIGINT   NOT NULL COMMENT '冗余',
    `stat_date`   DATE     NOT NULL COMMENT '统计日（Asia/Shanghai 切日）',
    `minutes`     INT      NOT NULL DEFAULT 0 COMMENT '当日该资源有效学习分钟数，封顶 120',
    `created_at`  DATETIME NOT NULL,
    `updated_at`  DATETIME NOT NULL,
    `deleted`     TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_resource_date` (`user_id`, `resource_id`, `stat_date`)
) ENGINE = InnoDB COMMENT ='学时日累计';

-- -----------------------------------------------------------------------------
-- 9. t_credit 学时流水
-- uk_credit_source 保证同日同资源的折算分录只有一条，重复折算不重复计入台账
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_credit`;
CREATE TABLE `t_credit`
(
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT        NOT NULL,
    `org_id`       BIGINT        NOT NULL COMMENT '冗余，台账按组织导出',
    `period_type`  TINYINT       NOT NULL COMMENT '1 自然年 / 2 五年规划期',
    `period_key`   VARCHAR(16)   NOT NULL COMMENT '2026 或 2021-2025',
    `source_type`  TINYINT       NOT NULL DEFAULT 1 COMMENT '来源，本期只有 1 学习',
    `source_id`    BIGINT        NOT NULL COMMENT '来源记录（t_credit_daily.id）',
    `minutes`      INT           NOT NULL COMMENT '该来源分录的分钟数',
    `credit`       DECIMAL(5, 2) NOT NULL COMMENT '折算学时 = minutes / 45',
    `occurred_on`  DATE          NOT NULL COMMENT '发生日',
    `created_at`   DATETIME      NOT NULL,
    `updated_at`   DATETIME      NOT NULL,
    `deleted`      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_credit_source` (`user_id`, `period_type`, `period_key`, `source_type`, `source_id`),
    KEY `idx_user_period` (`user_id`, `period_key`),
    KEY `idx_org_period` (`org_id`, `period_key`, `occurred_on`)
) ENGINE = InnoDB COMMENT ='学时流水';

-- -----------------------------------------------------------------------------
-- 10. t_credit_target 个人目标学时
-- 优先级：个人设置 > leader_flag 默认（书记 56/年、280/五年；普通 32/年、160/五年）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_credit_target`;
CREATE TABLE `t_credit_target`
(
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT        NOT NULL,
    `period_type`   TINYINT       NOT NULL COMMENT '1 自然年 / 2 五年规划期',
    `period_key`    VARCHAR(16)   NOT NULL,
    `target_credit` DECIMAL(6, 2) NOT NULL COMMENT '目标学时',
    `set_by`        BIGINT        NOT NULL COMMENT '设置人（组织管理员）',
    `set_at`        DATETIME      NOT NULL,
    `created_at`    DATETIME      NOT NULL,
    `updated_at`    DATETIME      NOT NULL,
    `deleted`       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_period` (`user_id`, `period_type`, `period_key`)
) ENGINE = InnoDB COMMENT ='个人目标学时';

-- -----------------------------------------------------------------------------
-- 11. t_exam_paper 试卷
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_exam_paper`;
CREATE TABLE `t_exam_paper`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `title`            VARCHAR(200) NOT NULL,
    `resource_id`      BIGINT                DEFAULT NULL COMMENT '关联学习资源',
    `question_ids_json` JSON        NOT NULL COMMENT '完整题目定义：[{id,type,score,stem,options,correctOptions}]，含标准答案，禁止直接返回客户端',
    `total_score`      INT          NOT NULL DEFAULT 100,
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '0 草稿 / 1 启用 / 2 停用',
    `created_at`       DATETIME     NOT NULL,
    `updated_at`       DATETIME     NOT NULL,
    `deleted`          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB COMMENT ='试卷';

-- -----------------------------------------------------------------------------
-- 12. t_exam_record 答题记录
-- 不变式：detail_json 落库的是服务端判分结果，不接收客户端判分
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_exam_record`;
CREATE TABLE `t_exam_record`
(
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT        NOT NULL,
    `paper_id`      BIGINT        NOT NULL,
    `org_id`        BIGINT        NOT NULL,
    `score`         DECIMAL(6, 2) NOT NULL,
    `correct_count` INT           NOT NULL,
    `total_count`   INT           NOT NULL,
    `detail_json`   JSON          NOT NULL COMMENT '服务端判分明细',
    `submitted_at`  DATETIME      NOT NULL,
    `created_at`    DATETIME      NOT NULL,
    `updated_at`    DATETIME      NOT NULL,
    `deleted`       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_paper` (`user_id`, `paper_id`),
    KEY `idx_org_submitted` (`org_id`, `submitted_at`)
) ENGINE = InnoDB COMMENT ='答题记录';

-- -----------------------------------------------------------------------------
-- 13. t_ranking_snapshot 排行榜快照
-- 个人榜按所属党支部（org_id）分片；管理端跨组织聚合查本表，不走 Redis
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_ranking_snapshot`;
CREATE TABLE `t_ranking_snapshot`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT,
    `scope_type`    TINYINT        NOT NULL COMMENT '1 个人 / 2 组织',
    `period_type`   TINYINT        NOT NULL COMMENT '1 周 / 2 月 / 3 季 / 4 年',
    `period_key`    VARCHAR(16)    NOT NULL COMMENT '2026-W38 / 2026-09 / 2026-Q3 / 2026',
    `org_id`        BIGINT         NOT NULL COMMENT '所在组织，个人榜为所属党支部',
    `user_id`       BIGINT         NOT NULL DEFAULT 0 COMMENT '个人榜时有值，组织榜为 0',
    `score`         DECIMAL(10, 2) NOT NULL,
    `rank_no`       INT            NOT NULL,
    `snapshot_date` DATE           NOT NULL,
    `created_at`    DATETIME       NOT NULL,
    `updated_at`    DATETIME       NOT NULL,
    `deleted`       TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scope_period` (`scope_type`, `period_type`, `period_key`, `snapshot_date`, `user_id`, `org_id`),
    KEY `idx_org_period` (`org_id`, `period_type`, `period_key`)
) ENGINE = InnoDB COMMENT ='排行榜快照';

-- -----------------------------------------------------------------------------
-- 14. t_audit_log 审核流水
-- 设计要点：待审队列就是本表 pending=1 的记录，
--          因此 party-audit 无需访问任何业务表即可提供待审列表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_audit_log`;
CREATE TABLE `t_audit_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `biz_type`    TINYINT      NOT NULL COMMENT '1 学习资源/2 用户实名/3 管理员申请/4 用户内容',
    `biz_id`      BIGINT       NOT NULL COMMENT '业务主键',
    `org_id`      BIGINT       NOT NULL COMMENT '归属组织，待审队列按数据范围过滤',
    `summary`     VARCHAR(200)          DEFAULT NULL COMMENT '待审队列展示用摘要',
    `action`      VARCHAR(32)  NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/TAKE_DOWN',
    `pending`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 仍是待审任务 / 0 已处理',
    `from_status` TINYINT               DEFAULT NULL,
    `to_status`   TINYINT               DEFAULT NULL,
    `operator_id` BIGINT       NOT NULL,
    `remark`      VARCHAR(500)          DEFAULT NULL COMMENT '审核意见，REJECT 时必填',
    `created_at`  DATETIME     NOT NULL,
    `updated_at`  DATETIME     NOT NULL,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_biz` (`biz_type`, `biz_id`, `created_at`),
    KEY `idx_pending` (`org_id`, `biz_type`, `pending`, `created_at`)
) ENGINE = InnoDB COMMENT ='审核流水';

-- -----------------------------------------------------------------------------
-- 15. t_sensitive_access_log 敏感数据访问日志（承接 spec N2 / AC24）
-- 仅解密或修改敏感字段时写入；列表取末四位不触发本日志
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_sensitive_access_log`;
CREATE TABLE `t_sensitive_access_log`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `biz_type`    VARCHAR(32) NOT NULL COMMENT '对象类型，如 USER_MOBILE',
    `biz_id`      BIGINT      NOT NULL COMMENT '对象主键',
    `action`      VARCHAR(16) NOT NULL COMMENT 'READ / UPDATE',
    `operator_id` BIGINT      NOT NULL,
    `scene`       VARCHAR(64) NOT NULL COMMENT '触发场景，如 LEDGER_EXPORT / ROSTER_DETAIL',
    `ip`          VARCHAR(64)          DEFAULT NULL,
    `created_at`  DATETIME    NOT NULL,
    `updated_at`  DATETIME    NOT NULL,
    `deleted`     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_biz` (`biz_type`, `biz_id`, `created_at`),
    KEY `idx_operator` (`operator_id`, `created_at`)
) ENGINE = InnoDB COMMENT ='敏感数据访问日志';

-- -----------------------------------------------------------------------------
-- 16. t_admin_application 管理员注册申请
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_admin_application`;
CREATE TABLE `t_admin_application`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT         NOT NULL,
    `org_id`        BIGINT         NOT NULL,
    `real_name`     VARCHAR(50)    NOT NULL,
    `mobile_cipher` VARBINARY(128)          DEFAULT NULL,
    `mobile_tail`   VARCHAR(4)              DEFAULT NULL,
    `proof_urls_json` JSON         NOT NULL COMMENT '身份证明图片列表',
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '0 待审核 / 1 通过 / 2 驳回',
    `reviewer_id`   BIGINT                  DEFAULT NULL,
    `reviewed_at`   DATETIME                DEFAULT NULL,
    `remark`        VARCHAR(500)            DEFAULT NULL,
    `created_at`    DATETIME       NOT NULL,
    `updated_at`    DATETIME       NOT NULL,
    `deleted`       TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    KEY `idx_status` (`status`, `created_at`)
) ENGINE = InnoDB COMMENT ='管理员注册申请';

-- -----------------------------------------------------------------------------
-- 基础数据：示例组织（三级树）
-- -----------------------------------------------------------------------------
INSERT INTO `t_org` (`id`, `name`, `level`, `parent_id`, `path`, `region_code`, `status`, `created_at`, `updated_at`)
VALUES (1, '中共红脉云筑示范县委', 1, 0, '/1/', '513200', 1, NOW(), NOW()),
       (2, '红脉云筑示范县城关镇党委', 2, 1, '/1/2/', '513201', 1, NOW(), NOW()),
       (3, '红脉云筑示范县第一村党支部', 3, 2, '/1/2/3/', '513201', 1, NOW(), NOW()),
       (4, '红脉云筑示范县第二村党支部', 3, 2, '/1/2/4/', '513201', 1, NOW(), NOW());
