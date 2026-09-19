# 红脉云筑 · 智慧党建小程序 Plan（V1 本期交付）

> 上游依据：`docs/spec.md`（已批准）
> 术语依据：`CONTEXT.md`
> 本文档回答「怎么做」。spec 与 plan 全部获批前不写实现代码。
> 本 plan 只详细设计 V1（F1-F11、F18-F21、N1-N9）。V2.1 之后的模块只声明归属位置，各自开发前补独立 plan。

---

## 一、架构概览

```
┌────────────────────────── 小程序端（微信原生） ──────────────────────────┐
│  主包：入口选择 + 登录实名（体积最小，保证冷启动）                          │
│  ├── packageUser  分包：用户端（学习 / 学时 / 答题 / 排行榜 / 我的）        │
│  └── packageAdmin 分包：管理端（台账 / 看板 / 审核 / 注册申请 / 导出）       │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ HTTPS + Bearer Token
┌───────────────────────────────▼─────────────────────────────────────────┐
│  party-boot  启动与装配（过滤器 / 拦截器 / 定时任务 / 定时任务装配）        │
├─────────────────────────────────────────────────────────────────────────┤
│  业务模块（每个模块内部统一四层：controller / service / domain / mapper）   │
│    party-audit    审核流水、待审队列、审核能力接口（依赖倒置）              │
│    party-content  学习资源内容管理                    F5, F6, F9          │
│    party-auth     认证、登录态、角色与数据范围         F1-F3, F20          │
│    party-org      组织树、党员台账、民族统计           F4, F18             │
│    party-learn    学习记录、学时折算与台账             F7, F8              │
│    party-exam     答题、排行榜、聚合看板               F10, F11, F19       │
│    party-assess   九项量化考核                        V2.1               │
│    party-activity 党建活动与档案                      V2.2               │
│    party-interact 评论・点赞・留言                    V3.1               │
│    party-notify   订阅消息与站内通知                   V3.2               │
│    party-i18n     文案码与多语言资源                   V3.3               │
├─────────────────────────────────────────────────────────────────────────┤
│  party-common  统一响应 / 错误码 / 异常 / 枚举 / 加密 / 脱敏 / 分页 / 工具  │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
     MySQL 8                 Redis 7              对象存储
  业务数据与流水          登录态 / 排行榜 ZSET    图片、字幕文件
```

**为什么是模块化单体**：单组织、单库、单团队场景，没有独立扩缩容诉求。按业务域拆 Maven 模块，拿到了模块边界与依赖管控，省掉注册中心、网关、链路追踪的运维成本。

**小程序端为什么这么分包**：主包体积直接决定冷启动速度，而四类用户进来第一眼都只需要「入口选择 + 登录」两页。管理端单独分包，普通用户永远不会下载到管理端代码。

---

## 二、技术决策

| 决策点 | 选择 | 理由 | 被否决的备选 |
|---|---|---|---|
| 服务端形态 | Spring Boot 3 + Java 17 模块化单体 | 单团队单库，模块边界足够，无独立扩容需求 | ① 微服务：引入注册中心/网关，运维成本远超收益 ② 单体不分模块：边界失控，后期无法按域拆服务 |
| 持久层 | MyBatis-Plus | 看板、台账、学时统计有大量聚合 SQL，需手写可控 | Spring Data JPA：复杂统计与动态条件查询别扭，难做 SQL 调优 |
| 认证 | 微信 code2session 换 openid + 自研 Token，登录态存 Redis | 可主动踢下线、可撤销授权、多实例无状态；满足 N1 越权拦截 | ① 纯 JWT：无法主动撤销，账号禁用后 Token 仍有效 ② Session：多实例需额外共享存储 |
| Token 有效期 | 7 天固定 TTL，不做滑动续期 | 党建类应用低频使用，滑动续期会让长期不活跃账号保持登录态，与 N1 的管控意图相悖 | 滑动续期：每次请求刷新 TTL，攻击面更大且实现更复杂 |
| 鉴权与数据范围 | 角色表 + 组织祖先路径前缀匹配 | 一次索引查询即可限定「本组织及下辖」，无需递归 | 递归查子组织：每请求多轮查询，1000 人组织下看板明显变慢 |
| 删除策略 | 逻辑删除（`deleted` 标记） | 党员台账、学时流水属审计类数据，物理删除会造成历史数据不可解释 | 物理删除：合规风险；且被删除用户的学时记录会散失 |
| 对外契约形态 | HTTP 状态码统一 200，业务结果由 body 的 `code` 表达 | 客户端只需一处错误处理分支；避免小程序端对 4xx/5xx 分别处理 | REST 语义化状态码：客户端要同时判断 HTTP 码与业务码，且反代与网关可能改写 4xx |
| 学时折算落点 | 服务端依据客户端上报的「学习时段」计算，客户端只报行为不报学时 | 客户端上报学时可直接伪造；学时是政策合规产物，必须可信 | 客户端直接上报累计学时：无法防刷，断网补传时无法做幂等校验 |
| 防刷策略 | 心跳上报 + 单位时间心跳数限流 + 单次增量上限 + 单条资源单日封顶 | 与 spec F8 折算口径一一对应，三层互相兜底 | 只校验总时长：无法判定「失焦超 10 分钟不计入」，也无法阻止短间隔连发 |
| 敏感字段存储 | 手机号存 `VARBINARY` 密文 + `VARCHAR(4)` 末四位冗余 | 列表直接取末四位渲染，从存储结构上杜绝「忘记脱敏」 | 明文存储 + 展示层脱敏：一旦漏掉某个接口的脱敏，敏感数据直接泄露 |
| 排行榜 | Redis ZSET 实时排名 + 每日快照落 MySQL | ZSET 排名 O(logN)，快照表支撑管理端跨组织聚合与历史周期查询 | 纯 SQL 实时聚合：1000 人 × 多周期下会超出 N3 的 3 秒 |
| 台账导出 | EasyExcel 写 xlsx 到对象存储，返回签名下载链接 | 流式写出，1000 人规模内存占用可控 | Apache POI 手写：需自行处理 SXSSF 与样式，代码量大；CSV：党务上报场景需要 xlsx |
| 小程序端 | 微信原生（WXML/WXSS/JS）+ 分包 | 需求明确要求可在微信开发者工具中直接调试 | ① uni-app / Taro：多一层编译，开发者工具里是编译产物，断点调试体验差 ② 两套独立小程序：与「一套双入口」冲突 |
| 主包/分包划分 | 主包仅入口与登录，用户端与管理端各一包 | 主包体积决定冷启动，与四类用户的首次路径匹配 | 单包：管理端页面代码被所有用户下载，主包体积不可控 |
| 多语言 | 客户端语言包 + 服务端返回文案码（messageKey） | 切换即时、离线可用；且 N6 要求字幕与内容不随语言变，客户端持有语言包才不会混淆两种语义 | 服务端渲染文案：切语言要重刷全部接口 |
| 图片与字幕存储 | 对象存储，小程序端直传 | 直传省服务端带宽，图片与字幕是主要流量来源 | 服务端中转上传：服务端带宽成瓶颈；存数据库 BLOB：不可行 |
| 审核状态迁移归属 | 依赖倒置：audit 定义 `AuditableHandler` 能力接口，业务模块实现并注册 | 审核要跨 content / interact 多张业务表迁移状态，若 audit 直接依赖业务模块会形成环 | audit 直接依赖 content/interact：模块依赖成环，且每加一种可审对象都要改 audit |

---

## 三、服务端设计

### 3.1 分层、依赖方向与依赖白名单

每个业务模块内部固定四层，依赖方向单向向下：

```
controller  →  service  →  domain  →  mapper
   (HTTP)      (编排/事务)   (模型/规则)   (持久化)
```

- **controller**：参数校验、身份与数据范围提取、调用 service、装配 VO。不含业务判断。
- **service**：事务边界、跨模块编排。对外暴露的接口全部定义在这一层。
- **domain**：实体、枚举、领域规则（学时折算、组织路径、心跳校验、状态机）。
- **mapper**：MyBatis-Plus Mapper + 自定义聚合 SQL（XML 置于 `resources/mapper/{module}/`）。

**模块依赖白名单**（P4 无环保证）：

```
party-common    ← 无依赖，被所有模块依赖
party-auth      → common
party-org       → common, auth
party-audit     → common                              （不依赖任何业务模块）
party-content   → common, auth, audit                 （实现 AuditableHandler）
party-learn     → common, auth, org, content
party-exam      → common, auth, org, content, learn
party-boot      → 全部业务模块（仅装配，不被反向依赖）
```

> **实现期修正**：原文将 party-audit 的依赖写为 `common, auth`，但 party-auth 需要实现 `AuditableHandler` 并调用 `AuditService`，实际方向是 auth → audit，原写法会成环。已修正为 **party-audit → common**（不依赖任何业务模块），由 auth、content 单向依赖 audit。

依赖图是严格分层的 DAG。后续版本：`party-assess → learn, exam, org`；`party-activity → org, exam`；`party-interact → audit, content, activity`；`party-notify → 各模块只读接口`；`party-i18n → common`。

**活动档案与留言的依赖方向**：spec F23 要求 V3.1 交付后把留言与合影纳入活动档案。档案归属 party-activity，若让 party-activity 反向读取 party-interact 会与 `party-interact → party-activity` 成环。因此**档案聚合的写入方与依赖方向保持同向**：由 party-interact 单向调用 party-activity 提供的 `ActivityArchiveService.appendInteraction(activityId, content)` 写入，party-activity 不反向依赖 party-interact。

**同模块内规则**：同一模块的 service 之间可直接互调（如 `LearnService` 调 `CreditService`）；**跨模块只允许调用对方 service 层的对外接口，禁止跨模块直接使用对方 mapper 或实体**。这条规则同时适用后续版本。

**审核为什么用依赖倒置**：`t_audit_log` 是唯一的审核流水落点，但状态迁移要发生在 `t_resource`（party-content）、后续的 `t_comment`（party-interact）等不同表上。若让 party-audit 反向依赖各业务模块，依赖图成环，且每新增一种可审对象都要改 party-audit。所以让 party-audit 只定义能力接口与自身流水表，业务模块实现接口并在完成迁移后调用 audit 落流水。

### 3.2 模块设计

#### party-common
**职责**：统一响应体、错误码与异常体系、分页约定、通用枚举、敏感字段加解密、脱敏与日期工具、组织路径工具。
**对外接口**：`R<T>`、`PageResult<T>`、`PageQuery`、`BizException`、`ErrorCode`、`CryptoUtil`、`MobileCipherHandler`（MyBatis 字段加密 TypeHandler）、`MaskUtil`、`OrgPathUtil`、`DateUtil`。
**依赖**：无。

#### party-audit
**职责**：审核流水记录、待审队列查询、可审对象的能力接口定义。
**对外接口**：`AuditService`、`AuditableHandler`、`AuditBizType`。
**依赖**：common、auth。

#### party-content
**职责**：学习资源的新增编辑、上下架、内容分类维护、用户端检索与详情、资源状态机。
**对外接口**：`ResourceService`。
**依赖**：common、auth、audit。

#### party-auth
**职责**：微信登录换取身份、实名信息提交与审核、登录态签发与校验、角色管理与入口鉴权、管理员注册申请与审核。
**对外接口**：`AuthService`、`AdminApplyService`。
**依赖**：common。

#### party-org
**职责**：组织树维护、组织祖先路径生成、数据范围解析、党员与积极分子名册、少数民族党员统计。
**对外接口**：`OrgService`、`RosterService`。
**依赖**：common、auth。

#### party-learn
**职责**：学习时段上报与防刷、学习记录与进度、学时日累计、学时折算与流水、个人目标学时管理、学时台账导出。
**对外接口**：`LearnService`、`CreditService`、`CreditTargetService`。
**依赖**：common、auth、org、content。

#### party-exam
**职责**：题库与试卷、在线答题与判分、排行榜实时维护与快照落库、管理端五项看板聚合。
**对外接口**：`ExamService`、`RankingService`、`DashboardService`。
**依赖**：common、auth、org、content、learn。

---

## 四、核心数据结构

### 4.1 通用约定

- **统一字段**：所有表含 `id BIGINT PK`、`created_at DATETIME NOT NULL`、`updated_at DATETIME NOT NULL`、`deleted TINYINT NOT NULL DEFAULT 0`。
- **时间与时区**：所有时间以 `Asia/Shanghai`（UTC+8）存 `DATETIME`；所有统计日（`stat_date`、`occurred_on`、`snapshot_date`）按 `Asia/Shanghai` 切日——即一次心跳在 23:50 计入当日、次日 00:10 计入次日。跨日学习时段按心跳发生时刻分别归属。
- **枚举**：一律 `TINYINT` 存库，Java 侧枚举映射，禁止裸 int 散落业务代码。
- **取整规则**：所有时长统计**以整秒累计、不做中途取整**；`minutes` 由秒数在写入 `t_credit_daily` 时按 `FLOOR(sec / 60)` 取整并累加，`credit` 保留 2 位小数由 `minutes / 45` 得出。展示层统一由 `credit` 派生，不再二次换算——这是 AC8「台账数值与个人页一致」成立的前提。
- **敏感字段**：手机号等存 `VARBINARY` 密文 + `VARCHAR(4)` 末四位；列表与详情一律取末四位，需要完整号码的行为（如导出）必须写入访问日志。
- **组织数据范围**：统一走 `t_org.path` 前缀匹配，`path` 形如 `/1/12/35/`。
- **NOT NULL 约定**：状态、枚举、计数类字段一律 `NOT NULL DEFAULT 0`；业务上可缺省的文本字段允许 `NULL`，但需在本节或字段表中标注。

### 4.2 t_org —— 组织

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| name | VARCHAR(100) | NOT NULL | 组织名称 |
| level | TINYINT | NOT NULL | 1 党委 / 2 党总支 / 3 党支部 |
| parent_id | BIGINT | NOT NULL DEFAULT 0 | 上级组织，根为 0 |
| path | VARCHAR(255) | NOT NULL | 祖先路径 `/1/12/35/`，带索引 |
| region_code | VARCHAR(12) | NULL | 行政区划代码 |
| status | TINYINT | NOT NULL DEFAULT 1 | 1 正常 / 0 停用 |

**不变式**：`path` 必须以 `/{id}/` 结尾；组织移动时须整棵子树重写 `path`。
**索引**：`idx_path(path)`、`idx_parent(parent_id)`。

### 4.3 t_user —— 用户与党员

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| open_id | VARCHAR(64) | NOT NULL | 微信 openid |
| real_name | VARCHAR(50) | NULL | 实名姓名，未实名时为空 |
| mobile_cipher | VARBINARY(128) | NULL | 手机号密文 |
| mobile_tail | VARCHAR(4) | NULL | 手机号末四位 |
| org_id | BIGINT | NULL | 所属组织，未实名时为空 |
| identity_type | TINYINT | NULL | 1 正式党员 / 2 预备党员 / 3 积极分子 / 4 群众 / 5 学生 |
| ethnicity | VARCHAR(20) | NULL | 民族 |
| leader_flag | TINYINT | NOT NULL DEFAULT 0 | 1 = 本组织党组织书记或班子成员，决定默认目标学时（见 4.10） |
| audit_status | TINYINT | NOT NULL DEFAULT 0 | 0 待审核 / 1 通过 / 2 驳回 |
| audit_remark | VARCHAR(255) | NULL | 审核意见 |
| avatar_url | VARCHAR(255) | NULL | 头像 |
| status | TINYINT | NOT NULL DEFAULT 1 | 1 正常 / 0 停用 |

**不变式**：`audit_status != 1` 的用户不计入台账、不参与排行与考核（对应 spec F2）。
**索引**：`uk_open_id(open_id)`、`idx_org_ethnicity(org_id, ethnicity)`、`idx_org_audit(org_id, audit_status)`。

### 4.4 t_user_role —— 用户角色（多角色）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| user_id | BIGINT | NOT NULL | 用户 |
| role | TINYINT | NOT NULL | 1 普通用户 / 2 积极分子 / 3 组织管理员 / 4 上级组织管理员 / 5 平台管理员 |

**唯一索引**：`uk_user_role(user_id, role)`。多角色存多行，支撑 spec F1「同一账号可同时持有两端身份」。

### 4.5 t_resource —— 学习资源（party-content）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| title | VARCHAR(200) | NOT NULL | 标题 |
| summary | VARCHAR(500) | NULL | 简介 |
| cover_url | VARCHAR(500) | NULL | 封面 |
| form | TINYINT | NOT NULL | 1 图文 / 2 视频 / 3 全景 / 4 VR |
| theme | TINYINT | NOT NULL | 1 党的历史 / 2 理论知识 / 3 先进事迹 |
| vr_theme | TINYINT | NULL | VR 专属：1 革命战争 / 2 建设成就 / 3 历史人物 / 4 党性修养 |
| vr_form | TINYINT | NULL | VR 专属：1 漫游类 / 2 展馆类 / 3 交互类 |
| content_url | VARCHAR(500) | NULL | 内容地址 |
| duration_sec | INT | NULL | 时长，用于展示；`form=1` 时可空 |
| **subtitle_url** | VARCHAR(500) | NULL | **字幕文件地址（承接 spec N6 与 AC28）** |
| **subtitle_lang** | VARCHAR(16) | NOT NULL DEFAULT 'zh-Hans' | **字幕语言，固定中文，不随界面语言切换** |
| status | TINYINT | NOT NULL DEFAULT 0 | 0 草稿 / 1 待审核 / 2 已上架 / 3 已下架 |
| sort_no | INT | NOT NULL DEFAULT 0 | 排序 |
| created_by | BIGINT | NOT NULL | 创建人 |

**不变式**：
- `form = 4`（VR）时 `vr_theme`、`vr_form` 必填，且 `content_url` 必须为空——VR 类只在用户端展示简介，不对接播放。
- `form IN (2,3)`（视频、全景）时 `subtitle_url` 必填（AC28）；`form IN (1,4)` 时 `subtitle_url` 必须为空。
- `subtitle_lang` 恒为 `zh-Hans`，界面语言切换不影响该字段。

**索引**：`idx_status_form_theme(status, form, theme)`。

### 4.6 t_learn_session —— 学习时段（会话行）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| session_id | VARCHAR(64) | NOT NULL | 客户端会话标识 |
| user_id / resource_id | BIGINT | NOT NULL | 归属 |
| org_id | BIGINT | NOT NULL | 冗余，看板按组织聚合用 |
| begin_at | DATETIME | NOT NULL | 时段开始 |
| last_end_at | DATETIME | NOT NULL | 最后一次被接受的心跳时刻 |
| last_seq | INT | NOT NULL DEFAULT 0 | 本会话已接受的最大 `client_seq`，新心跳必须 > 该值 |
| unfocused_sec | INT | NOT NULL DEFAULT 0 | 本会话累计失焦秒数 |
| valid_sec | INT | NOT NULL DEFAULT 0 | 本会话累计有效秒数 |
| source | TINYINT | NOT NULL DEFAULT 1 | 1 在线 / 2 断网补传 |
| reported_at | DATETIME | NOT NULL | 最后上报时间 |

**唯一索引**：`uk_session_id(session_id)`。**索引**：`idx_user_resource(user_id, resource_id)`。

### 4.7 t_learn_heartbeat —— 心跳明细（幂等与审计）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| session_id | VARCHAR(64) | NOT NULL | 会话 |
| client_seq | INT | NOT NULL | 客户端自增序号（会话内从 1 起） |
| delta_sec | INT | NOT NULL | 上报间隔秒数 |
| focused_sec | INT | NOT NULL | 上报的聚焦秒数 |
| position_sec | INT | NOT NULL | 播放位置 |
| accepted_sec | INT | NOT NULL DEFAULT 0 | 服务端认定计入的有效秒数 |
| reject_reason | VARCHAR(32) | NULL | 被拒原因：`REPLAY` / `OUT_OF_ORDER` / `RATE_LIMITED` / `OVER_DELTA` |
| reported_at | DATETIME | NOT NULL | 上报时间 |

**唯一索引**：`uk_session_seq(session_id, client_seq)` —— 断网补传重复上报时直接命中，天然幂等。

**为什么要两张表**：`t_learn_heartbeat` 保证幂等（重复上报能查到已存在的行），`t_learn_session` 保证序号单调（新心必须大于 `last_seq`）。**序号空间统一为「会话内」，不复用「日 + 资源」粒度**——用户同日重进同一资源会产生新会话，序号从 1 重新计数，不会与旧会话的序号冲突。

### 4.8 t_learn_record —— 学习记录（用户 × 资源聚合）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id / resource_id | BIGINT | NOT NULL | 联合唯一 |
| org_id | BIGINT | NOT NULL | 冗余 |
| last_position_sec | INT | NOT NULL DEFAULT 0 | 上次进度位置，续播用 |
| progress_pct | DECIMAL(5,2) | NOT NULL DEFAULT 0 | 完成百分比，取值 0-100，= `min(100, round(last_position_sec / duration_sec × 100, 2))`，`duration_sec` 为空时恒为 0 |
| valid_sec | INT | NOT NULL DEFAULT 0 | 累计有效学习秒数 |
| unread_flag | TINYINT | NOT NULL DEFAULT 1 | 是否未开始 |
| first_start_at / last_learn_at / finish_at | DATETIME | NULL | 时间轴 |

**唯一索引**：`uk_user_resource(user_id, resource_id)`。

### 4.9 t_credit_daily —— 学时日累计

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id / resource_id | BIGINT | NOT NULL | 归属 |
| org_id | BIGINT | NOT NULL | 冗余 |
| stat_date | DATE | NOT NULL | 统计日（Asia/Shanghai 切日） |
| minutes | INT | NOT NULL DEFAULT 0 | 当日该资源有效学习分钟数，**封顶 120** |

**唯一索引**：`uk_user_resource_date(user_id, resource_id, stat_date)`。
**不变式**：`0 ≤ minutes ≤ 120` —— 对应 spec F8「单条资源单日最多计 2 学时」。口径约束收敛到一处，避免在 service 里反复判断。

### 4.10 t_credit —— 学时流水

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | 用户 |
| org_id | BIGINT | NOT NULL | 冗余，台账按组织导出用 |
| period_type | TINYINT | NOT NULL | 1 自然年 / 2 五年规划期 |
| period_key | VARCHAR(16) | NOT NULL | `2026` 或 `2021-2025` |
| source_type | TINYINT | NOT NULL DEFAULT 1 | 学时来源，本期只有 1 学习 |
| source_id | BIGINT | NOT NULL | 来源记录（`t_credit_daily.id`） |
| minutes | INT | NOT NULL | 该来源分录的分钟数 |
| credit | DECIMAL(5,2) | NOT NULL | 折算学时 = `minutes / 45`，保留 2 位 |
| occurred_on | DATE | NOT NULL | 发生日（= 来源的 `stat_date`） |

**唯一索引**：`uk_credit_source(user_id, period_type, period_key, source_type, source_id)` —— 保证同日同资源的折算分录只有一条，重复折算不会重复计入台账。
**索引**：`idx_user_period(user_id, period_key)`、`idx_org_period(org_id, period_key, occurred_on)`。

### 4.11 t_credit_target —— 个人目标学时

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | 用户 |
| period_type | TINYINT | NOT NULL | 1 自然年 / 2 五年规划期 |
| period_key | VARCHAR(16) | NOT NULL | `2026` 或 `2021-2025` |
| target_credit | DECIMAL(6,2) | NOT NULL | 目标学时 |
| set_by | BIGINT | NOT NULL | 设置人（组织管理员） |
| set_at | DATETIME | NOT NULL | 设置时间 |

**唯一索引**：`uk_user_period(user_id, period_type, period_key)`。

**目标学时取值优先级**（对应 spec F8）：

1. `t_credit_target` 中存在该用户该周期的记录 → 取其 `target_credit`；
2. 否则按 `t_user.leader_flag` 取默认值：
   - `leader_flag = 1`（书记及班子成员）：自然年 **56** 学时、五年规划期 **280** 学时；
   - `leader_flag = 0`：自然年 **32** 学时、五年规划期 **160** 学时。
3. 自然年值由五年值除以 5 得出，两者独立存入默认值表，不做运行时换算。

**关于「每年脱产 ≥ 40 学时」**：这是线下集中培训要求，小程序无法采集其数据。个人页在达标进度旁单独展示该政策提示文案，**不计入线上达标率的分母**。这条边界需在 V1 验收时向业务方明确。

### 4.12 t_exam_paper / t_exam_record

`t_exam_paper`：`id`、`title VARCHAR(200)`、`resource_id BIGINT NULL`、`question_ids_json JSON`、`total_score INT`、`status TINYINT`（0 草稿 / 1 启用 / 2 停用）。

`t_exam_record`：`id`、`user_id`、`paper_id`、`org_id`、`score DECIMAL(6,2)`、`correct_count INT`、`total_count INT`、`detail_json JSON`、`submitted_at DATETIME`。

**不变式**：`detail_json` 落库的是服务端判分结果，不接收客户端判分。`uk_user_paper(user_id, paper_id)` 保证同一份试卷只提交一次。

### 4.13 t_ranking_snapshot —— 排行榜快照

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| scope_type | TINYINT | NOT NULL | 1 个人 / 2 组织 |
| period_type | TINYINT | NOT NULL | 1 周 / 2 月 / 3 季 / 4 年 |
| period_key | VARCHAR(16) | NOT NULL | `2026-W37`、`2026-09` |
| org_id | BIGINT | NOT NULL | 所在组织（个人榜为所属党支部 id） |
| user_id | BIGINT | NOT NULL DEFAULT 0 | 个人榜时有值，组织榜为 0 |
| score | DECIMAL(10,2) | NOT NULL | 分值 |
| rank_no | INT | NOT NULL | 名次 |
| snapshot_date | DATE | NOT NULL | 快照日 |

**唯一索引**：`uk_scope_period(scope_type, period_type, period_key, snapshot_date, user_id, org_id)`。
**索引**：`idx_org_period(org_id, period_type, period_key)` —— 支撑管理端按组织范围聚合。

### 4.14 t_audit_log —— 审核流水（party-audit）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| biz_type | TINYINT | NOT NULL | 1 学习资源 / 2 用户实名 / 3 管理员申请 / 4 用户内容(V3.1) |
| biz_id | BIGINT | NOT NULL | 业务主键 |
| org_id | BIGINT | NOT NULL | 归属组织，待审队列按数据范围过滤用 |
| action | VARCHAR(32) | NOT NULL | `SUBMIT` / `APPROVE` / `REJECT` / `TAKE_DOWN` |
| pending | TINYINT | NOT NULL DEFAULT 1 | 1 = 仍是待审任务，0 = 已处理。`SUBMIT` 置 1，`APPROVE`/`REJECT`/`TAKE_DOWN` 置 0 |
| from_status / to_status | TINYINT | NULL | 状态迁移前后值 |
| operator_id | BIGINT | NOT NULL | 操作人 |
| remark | VARCHAR(500) | NULL | 审核意见，`REJECT` 时必填 |

**索引**：`idx_biz(biz_type, biz_id, created_at)`、`idx_pending(org_id, biz_type, pending, created_at)`。

**设计要点**：待审队列就是本表中 `pending = 1` 的记录，因此 party-audit **不需要访问任何业务表**即可提供待审列表——这是依赖倒置得以成立的关键。

### 4.15 t_sensitive_access_log —— 敏感数据访问日志（承接 spec N2）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| biz_type | VARCHAR(32) | NOT NULL | 对象类型，如 `USER_MOBILE` |
| biz_id | BIGINT | NOT NULL | 对象主键 |
| action | VARCHAR(16) | NOT NULL | `READ` / `UPDATE` |
| operator_id | BIGINT | NOT NULL | 操作人 |
| scene | VARCHAR(64) | NOT NULL | 触发场景，如 `LEDGER_EXPORT`、`ROSTER_DETAIL` |
| ip | VARCHAR(64) | NULL | 来源 IP |

**索引**：`idx_biz(biz_type, biz_id, created_at)`、`idx_operator(operator_id, created_at)`。

**写入时机**：解密手机号密文或修改敏感字段时由 `CryptoUtil` 的调用方在同一事务内写入。列表与详情取末四位不触发本日志——脱敏展示不算访问敏感数据。

### 4.16 t_admin_application —— 管理员注册申请（party-auth）

`id`、`user_id BIGINT NOT NULL`、`org_id BIGINT NOT NULL`、`real_name VARCHAR(50)`、`mobile_cipher VARBINARY(128)`、`mobile_tail VARCHAR(4)`、`proof_urls_json JSON`（身份证明图片）、`status TINYINT`（0 待审核 / 1 通过 / 2 驳回）、`reviewer_id BIGINT NULL`、`reviewed_at DATETIME NULL`、`remark VARCHAR(500) NULL`。

**索引**：`idx_status(status, created_at)`、`uk_user(user_id)`（一人同时只有一份有效申请）。

### 4.17 V2+ 预留表（本期不建）

`t_indicator`、`t_assess_period`、`t_indicator_score`、`t_evidence_material`、`t_org_score`、`t_activity`、`t_activity_sign_in`、`t_activity_archive`、`t_comment`、`t_reaction`、`t_notification`、`t_user_notify_pref`、`t_i18n_message`。

---

## 五、核心接口

### 5.1 通用约定

**统一响应体**：

```java
public class R<T> {
    private int code;          // 0 成功
    private String messageKey; // 文案码，客户端按当前语言渲染（承接 spec N9）
    private String message;    // 中文兜底文案，便于排障
    private T data;
    private String traceId;
}
```

**错误码段**：

| 段 | 含义 |
|---|---|
| 0 | 成功 |
| 1000-1999 | 参数与校验 |
| 2000-2999 | 认证与登录态 |
| 3000-3999 | 权限与数据范围 |
| 4000-4999 | 业务规则 |
| 5000-5999 | 系统与外部依赖 |

**已用错误码**：`2001` 登录态失效、`2002` 微信 code 无效、`3001` 越权、`3002` 无该入口权限、`4001` 手机号已被占用、`4002` 重复提交试卷、`4003` 心跳过于频繁、`4004` 审核状态不允许该迁移。

**分页约定**：请求 `pageNo` 从 1 起、`pageSize` 上限 100；响应 `PageResult<T> { pageNo, pageSize, total, records }`。

**身份注入**：所有接口的操作员身份由 `AuthInterceptor` 从 Token 解析注入，**controller 不接受客户端传入的操作员 id**。

### 5.2 出入参定义

**party-auth**

| LoginResult | 类型 | 说明 |
|---|---|---|
| token | String | 登录令牌 |
| realNameStatus | Integer | 0 待审核 / 1 通过 / 2 驳回 / 9 未提交 |
| entries | List\<EntryVO\> | 可进入的入口，`EntryVO{ code, name }`，code 取 `USER` / `ADMIN` |
| nickname | String | 微信昵称 |

| RealNameCmd | 类型 | 必填 | 校验 |
|---|---|---|---|
| realName | String(50) | 是 | 2-50 字 |
| mobile | String(11) | 是 | 1[3-9]\d{9} |
| orgId | Long | 是 | 须为已存在且 status=1 的组织 |
| identityType | Integer | 是 | 1-5 |
| ethnicity | String(20) | 是 | 非空 |
| avatarUrl | String(255) | 否 | |

| RealNameApplyVO | 类型 | 说明 |
|---|---|---|
| userId | Long | 用户 id |
| realName / mobile（末四位） / orgName / identityType / ethnicity | String / Integer | 申请信息 |
| submittedAt | DateTime | 提交时间 |

| AdminApplyCmd | 类型 | 必填 | 校验 |
|---|---|---|---|
| realName | String(50) | 是 | 2-50 字 |
| mobile | String(11) | 是 | 手机号格式 |
| orgId | Long | 是 | 申请管理的组织 |
| proofUrls | List\<String\> | 是 | 身份证明图片，1-5 张 |

| AdminApplyVO | 类型 | 说明 |
|---|---|---|
| id / userId / realName / mobile（末四位） / orgName | | 申请信息 |
| proofUrls | List\<String\> | 证明图片 |
| status | Integer | 0 待审核 / 1 通过 / 2 驳回 |
| remark / submittedAt / reviewedAt | | 审核信息 |

**party-org**

| RosterQuery | 类型 | 必填 | 说明 |
|---|---|---|---|
| orgId | Long | 否 | 不传时取操作员所在组织 |
| keyword | String | 否 | 姓名模糊检索，最长 20 字 |
| ethnicity | String(20) | 否 | 民族筛选 |
| identityType | Integer | 否 | 身份类型筛选 |
| auditStatus | Integer | 否 | 默认 1（仅已通过） |
| pageNo / pageSize | Integer | 是 | 分页 |

| RosterItemVO | 类型 | 说明 |
|---|---|---|
| userId / realName / mobile（末四位） | | 基本信息 |
| orgId / orgName / orgPath | | 组织信息 |
| identityType / ethnicity / leaderFlag | Integer / String / Integer | 身份、民族、是否班子成员 |
| totalCredit | BigDecimal | 当期累计学时 |
| lastLearnAt | DateTime | 最近学习时间 |

| EthnicityStatVO | 类型 | 说明 |
|---|---|---|
| ethnicity | String | 民族 |
| count | Integer | 人数 |
| ratio | BigDecimal | 占比，保留 2 位小数，分母为范围内已审核通过的党员与积极分子总数 |

**party-content**

| ResourceFilter | 类型 | 说明 |
|---|---|---|
| theme | Integer 否 | 内容主题 |
| form | Integer 否 | 资源形态 |
| vrTheme / vrForm | Integer 否 | VR 专属分类 |
| keyword | String 否 | 标题或简介关键词，最长 30 字 |

| ResourceSaveCmd | 类型 | 必填 | 校验 |
|---|---|---|---|
| title | String(200) | 是 | |
| summary | String(500) | 否 | |
| coverUrl | String(500) | 否 | |
| form | Integer | 是 | 1-4 |
| theme | Integer | 是 | 1-3 |
| vrTheme / vrForm | Integer | 条件必填 | `form=4` 时必填 |
| contentUrl | String(500) | 条件必填 | `form≠4` 时必填；`form=4` 时必须为空 |
| subtitleUrl | String(500) | 条件必填 | `form∈{2,3}` 时必填；否则必须为空 |
| durationSec | Integer | 否 | `form≠1` 时必填，>0 |
| sortNo | Integer | 否 | 默认 0 |

| ResourceCardVO | 类型 | 说明 |
|---|---|---|
| id / title / summary / coverUrl | | 展示信息 |
| form / theme / vrTheme / vrForm | Integer | 分类 |
| durationSec | Integer | 时长 |
| progressPct | BigDecimal | 当前用户完成度，未登录时为空 |

| ResourceDetailVO | 类型 | 说明 |
|---|---|---|
| id / title / summary / coverUrl / contentUrl | | 内容 |
| form / theme / vrTheme / vrForm / durationSec | Integer | 分类与时长 |
| **subtitleUrl** | String | **字幕地址（`form∈{2,3}` 时非空）** |
| **subtitleLang** | String | **固定 `zh-Hans`** |
| experienceHint | String | `form=4` 时的到站体验引导文案，此时 `contentUrl` 恒为空 |
| progressPct / lastPositionSec | BigDecimal / Integer | 续播信息 |

**party-learn**

| HeartbeatCmd | 类型 | 必填 | 校验 |
|---|---|---|---|
| resourceId | Long | 是 | 资源须为已上架 |
| sessionId | String(64) | 是 | 客户端生成，全局唯一 |
| clientSeq | Integer | 是 | 会话内自增，从 1 起 |
| deltaSec | Integer | 是 | 距上次心跳的秒数，1 ≤ deltaSec ≤ 300 |
| focusedSec | Integer | 是 | 本周期聚焦秒数，**`form=1` 时固定等于 `deltaSec`** |
| positionSec | Integer | 是 | 播放位置，≥0 |
| source | Integer | 是 | 1 在线 / 2 断网补传 |

| HeartbeatResult | 类型 | 说明 |
|---|---|---|
| acceptedSec | Integer | 服务端认定本条计入的有效秒数；被判重放/限流时为 0 |
| progressPct | BigDecimal | 最新完成度 |
| todayMinutes | Integer | 该资源当日已计分钟数 |
| todayCredit | BigDecimal | 该资源当日折算学时 |
| accepted | Boolean | 本条是否被接受 |

| LearnRecordVO | 类型 | 说明 |
|---|---|---|
| resourceId / title / coverUrl / form | | 资源信息 |
| progressPct / validSec | BigDecimal / Integer | 进度与累计有效秒数 |
| firstStartAt / lastLearnAt / finishAt | DateTime | 时间轴 |

| CreditOverviewVO | 类型 | 说明 |
|---|---|---|
| periodType / periodKey | Integer / String | 统计口径 |
| earnedCredit | BigDecimal | 已获学时 |
| targetCredit | BigDecimal | 目标学时（取值优先级见 4.11） |
| progressPct | BigDecimal | 达标进度，上限 100 |
| leaderFlag | Integer | 是否班子成员，决定是否展示脱产提示 |
| offlineHint | String | 班子成员时为「每年脱产集中培训不少于 40 学时」提示文案，其余为空 |
| dailyCapHint | String | 折算口径说明文案 |

| ExportResult | 类型 | 说明 |
|---|---|---|
| fileUrl | String | 带时效签名的下载链接，有效期 30 分钟 |
| fileName | String | 文件名，如 `学时台账_XX党支部_2026.xlsx` |
| rowCount | Integer | 导出行数 |
| expireAt | DateTime | 链接过期时间 |

**party-exam**

| PaperVO | 类型 | 说明 |
|---|---|---|
| paperId / title / totalScore | | 试卷信息 |
| questions | List\<QuestionVO\> | `QuestionVO{ questionId, content, options:[{key,text}] }`，**不含答案** |

| ExamResultVO | 类型 | 说明 |
|---|---|---|
| score / correctCount / totalCount | BigDecimal / Integer | 判分结果 |
| details | List\<AnswerDetailVO\> | `AnswerDetailVO{ questionId, correctAnswer, userAnswer, isCorrect, analysis }` |

| RankingMetric | 枚举 | 说明 |
|---|---|---|
| LEARN_CREDIT | | 学时变动，分值增量 = 新增学时 |
| EXAM_SCORE | | 答题得分，分值增量 = 得分 |

| RankingQuery | 类型 | 必填 | 说明 |
|---|---|---|---|
| scopeType | Integer | 是 | 1 个人 / 2 组织 |
| orgId | Long | 否 | 组织节点；不传时取操作员所在组织。普通用户强制为其所属组织 |
| periodType | Integer | 是 | 1 周 / 2 月 / 3 季 / 4 年 |
| periodKey | String(16) | 否 | 缺省取当前周期 |

| RankingVO | 类型 | 说明 |
|---|---|---|
| scopeType / periodType / periodKey | | 查询口径 |
| topList | List\<RankingItemVO\> | `RankingItemVO{ rankNo, userId, orgId, name, orgName, score }` |
| selfRank | RankingItemVO | 仅普通用户请求时有值，本人名次；未上榜时 `rankNo=0` |
| fullList | List\<RankingItemVO\> | 仅管理员请求时有值，数据范围内完整榜单 |

| RankingMetric 用法 | 说明 |
|---|---|
| 个人榜 | Redis key `rank:USER:{orgId}:{periodType}:{periodKey}`，member = userId |
| 组织榜 | Redis key `rank:ORG:{periodType}:{periodKey}`，member = orgId |
| 管理端跨组织查询 | **不走 Redis**，直接查 `t_ranking_snapshot` 按 `org_id IN (可见组织)` 聚合 |
| 快照 | 每日 02:00 由定时任务把 Redis ZSET 落成快照 |

**party-console（已并入 party-exam）**

| DashboardVO | 类型 | 说明 |
|---|---|---|
| memberCount | Integer | 在册人数 = 范围内 `identity_type IN (1,2,3)` 且 `audit_status=1` 的用户数 |
| activeCount | Integer | 周期内有学习记录的人数 |
| activeRate | BigDecimal | 活跃度 = `activeCount / memberCount`，分母为 0 时返回 0 |
| creditReachRate | BigDecimal | 学时达标率 = 达到目标学时的人数 / memberCount |
| scoreDistribution | List\<ScoreBucketVO\> | `ScoreBucketVO{ bucket, count }`，bucket 取 `0-59` / `60-69` / `70-79` / `80-89` / `90-100` |

> **口径说明**：在册人数的口径是「党员与积极分子」（`identity_type IN (1,2,3)`），**不是**全体已审核通过用户——审核通过的群众与学生不计入，否则看板数值与台账明细对不上，AC20「与台账明细核对一致」将不成立。

**party-audit**

| AuditBizType | 枚举 | 目标表 | 状态字段 | 合法迁移 |
|---|---|---|---|---|
| RESOURCE | | t_resource | status | 待审核 → 已上架 / 草稿；已上架 → 已下架 |
| REAL_NAME | | t_user | audit_status | 待审核 → 通过 / 驳回 |
| ADMIN_APPLY | | t_admin_application | status | 待审核 → 通过 / 驳回 |
| USER_CONTENT | | t_comment（V3.1） | status | 待审核 → 通过 / 驳回 / 删除 |

| AuditTaskVO | 类型 | 说明 |
|---|---|---|
| logId / bizType / bizId / orgId | | 任务标识 |
| orgName / summary | String | 组织名与摘要文案（由业务模块在提交时写入流水） |
| submittedAt | DateTime | 提交时间 |
| operatorId / operatorName | Long / String | 提交人 |

### 5.3 party-auth

```java
public interface AuthService {
    /**
     * 微信登录。入参为 wx.login 得到的临时 code。
     * 调微信 code2session 换 openid，查或建 t_user，计算可进入的入口，签发 Token。
     * 失败：2002 code 无效或已过期。
     */
    LoginResult loginByWechat(String jsCode);

    /**
     * 提交实名信息。写入 t_user 实名字段并置 audit_status=0，
     * 同时经 AuditService.record 落一条 biz_type=REAL_NAME 的 SUBMIT 流水。
     * 失败：4001 手机号已被其他账号实名占用。
     */
    void submitRealName(long userId, RealNameCmd cmd);

    /** 分页查看待审核实名申请。数据范围：仅本组织及下辖组织。 */
    PageResult<RealNameApplyVO> pageRealNameApply(long operatorId, PageQuery query);

    /** 审核实名申请。pass=false 时 remark 必填；成功后迁移 audit_status 并落流水。 */
    void reviewRealName(long operatorId, long targetUserId, boolean pass, String remark);

    /**
     * 进入指定入口时的鉴权。entry=ADMIN 要求账号持有 role ∈ {3,4,5}。
     * 失败：3002 无该入口权限。
     */
    void assertEntryAllowed(long userId, EntryType entry);
}

public interface AdminApplyService {
    /**
     * 提交管理员注册申请。校验该用户尚无有效申请，写 t_admin_application(status=0)。
     * 失败：4005 已存在待审核或已通过的申请。
     */
    long submit(long userId, AdminApplyCmd cmd);

    /** 平台管理员分页查看申请。 */
    PageResult<AdminApplyVO> page(long operatorId, Integer status, PageQuery query);

    /**
     * 审核申请。通过时：t_admin_application.status=1，
     * 并向 t_user_role 插入 role=3（组织管理员）或 4（上级组织管理员）。
     * pass=false 时 remark 必填。
     * 失败：4004 审核状态不允许该迁移。
     */
    void review(long operatorId, long applyId, boolean pass, String remark);
}
```

### 5.4 party-org

```java
public interface OrgService {
    /** 生成或重写组织祖先路径，返回形如 /1/12/35/ 的 path。 */
    String buildPath(long orgId, long parentId);

    /** 解析某操作员可见的组织 id 列表。平台管理员返回全部，其余按其 org path 前缀匹配。 */
    List<Long> resolveVisibleOrgIds(long operatorId);
}

public interface RosterService {
    /** 按数据范围分页查询名册。默认只返回 audit_status=1 的用户。 */
    PageResult<RosterItemVO> pageRoster(long operatorId, RosterQuery query);

    /**
     * 少数民族党员统计。分母为范围内 identity_type IN (1,2,3) 且 audit_status=1 的人数，
     * 与 pageRoster 的口径一致。
     */
    List<EthnicityStatVO> statEthnicity(long operatorId, long orgId);
}
```

### 5.5 party-content

```java
public interface ResourceService {
    /** 用户端分页检索：仅返回 status=已上架 的资源。 */
    PageResult<ResourceCardVO> pageForUser(long userId, PageQuery query, ResourceFilter filter);

    /** 用户端资源详情。form=4 时返回 experienceHint，contentUrl 恒为空。 */
    ResourceDetailVO detailForUser(long userId, long resourceId);

    /**
     * 平台管理员新增或编辑资源。落库 status=待审核，
     * 并经 AuditService.record 落 SUBMIT 流水。返回资源 id。
     */
    long saveResource(long operatorId, ResourceSaveCmd cmd);

    /**
     * 上下架。仅允许 待审核→已上架、待审核→草稿、已上架→已下架 三种迁移。
     * 迁移后落流水。失败：4004 状态不允许该迁移。
     */
    void changeStatus(long operatorId, long resourceId, ResourceStatus target, String remark);
}
```

### 5.6 party-learn

```java
public interface LearnService {
    /**
     * 学习心跳上报。校验顺序：
     *   1. t_learn_heartbeat 中 (sessionId, clientSeq) 已存在 → 直接返回该次结果（幂等）
     *   2. clientSeq ≤ t_learn_session.last_seq → 记 reject_reason=OUT_OF_ORDER，acceptedSec=0
     *   3. 距上次心跳 < 5 秒或本分钟心跳数 > 12 → 记 RATE_LIMITED，失败 4003
     *   4. deltaSec > 300 → 记 OVER_DELTA，按 300 计
     *   5. 计算有效秒数（见 6.3 的失焦口径）
     * 然后同事务更新 t_learn_session、t_learn_record、t_credit_daily，
     * 并调用 CreditService.accrue 落 t_credit 分录。
     */
    HeartbeatResult reportHeartbeat(long userId, HeartbeatCmd cmd);

    /** 个人学习记录分页。 */
    PageResult<LearnRecordVO> pageMyRecords(long userId, PageQuery query);
}

public interface CreditService {
    /** 个人学时概览。目标学时按 4.11 的优先级取值。 */
    CreditOverviewVO overview(long userId, PeriodType periodType, String periodKey);

    /**
     * 学时台账导出。数据范围为操作员可见组织。
     * 写一条 biz_type=USER_MOBILE、scene=LEDGER_EXPORT 的敏感访问日志。
     */
    ExportResult exportOrgLedger(long operatorId, long orgId, PeriodType periodType, String periodKey);

    /**
     * 学时入账。由 LearnService 在同一事务内调用。
     * 依据 t_credit_daily 的最新 minutes 与已入账分录的差值，写或更新 t_credit 分录
     * （唯一键 uk_credit_source 保证同日同资源只一条）。
     */
    void accrue(long userId, long orgId, long resourceId, long creditDailyId,
                PeriodType periodType, String periodKey, LocalDate statDate, int totalMinutes);
}

public interface CreditTargetService {
    /** 组织管理员为本组织成员设置目标学时。 */
    void setMemberTarget(long operatorId, long userId, PeriodType periodType,
                         String periodKey, BigDecimal targetCredit);

    /** 取生效目标学时：先查个人设置，无则按 leader_flag 取默认值。 */
    BigDecimal resolveTarget(long userId, PeriodType periodType, String periodKey);
}
```

### 5.7 party-exam

```java
public interface ExamService {
    /** 取试卷（不下发答案）。 */
    PaperVO getPaper(long userId, long paperId);

    /**
     * 提交答题。服务端判分，写 t_exam_record，
     * 并调用 RankingService.addScore(userId, orgId, EXAM_SCORE, score)。
     * 失败：4002 重复提交同一份试卷。
     */
    ExamResultVO submit(long userId, long paperId, Map<Long, String> answers);
}

public interface RankingService {
    /**
     * 查询榜单。普通用户：返回 topList（前 20）+ selfRank，orgId 强制为其所属组织，走 Redis。
     * 管理员：返回 fullList，按 orgId 与可见组织范围查 t_ranking_snapshot 聚合。
     */
    RankingVO query(long operatorId, RankingQuery query);

    /** 分值增量更新。个人榜写 rank:USER:{orgId}:...，组织榜同步累加 rank:ORG:...。 */
    void addScore(long userId, long orgId, RankingMetric metric, double delta);
}

public interface DashboardService {
    /** 管理端看板五项指标。口径见 5.2 DashboardVO 说明。 */
    DashboardVO overview(long operatorId, long orgId, PeriodType periodType, String periodKey);
}
```

### 5.8 party-audit

```java
/** 业务模块实现本接口并注册为 Spring Bean，由 AuditService 在审核时回调。 */
public interface AuditableHandler {
    /** 本处理器负责的可审对象类型。 */
    AuditBizType bizType();

    /**
     * 迁移业务状态。实现方负责校验合法迁移并在同一事务内更新业务表。
     * 失败：4004 状态不允许该迁移。
     */
    void migrate(long bizId, AuditAction action, String remark);
}

public interface AuditService {
    /**
     * 落一条 SUBMIT 流水（pending=1）。由业务模块在自身状态置为「待审核」后调用。
     * summary 为待审队列展示用的摘要文案。
     */
    void record(long orgId, AuditBizType bizType, long bizId, String summary, long operatorId);

    /**
     * 审核。查 AuditableHandler(bizType) 并回调 migrate，
     * 成功后落 APPROVE / REJECT 流水并把该 bizId 的 pending 置 0。
     * pass=false 时 remark 必填。
     */
    void review(long operatorId, AuditBizType bizType, long bizId, boolean pass, String remark);

    /** 待审队列分页。数据范围为操作员可见组织，仅查 pending=1。 */
    PageResult<AuditTaskVO> pagePending(long operatorId, AuditBizType bizType, PageQuery query);
}
```

**审核写入路径唯一性**：`t_audit_log` 只有 `AuditService.record` 与 `AuditService.review` 两个写入入口。业务模块**不得**自行写这张表——这条约束消除了「同一动作两条写入路径」的可能。

---

## 六、模块交互

### 6.1 登录、实名与管理员授权（F1、F2、F3、F20）

```
【普通用户登录】
小程序 wx.login → code
  → POST /auth/login → AuthService.loginByWechat(code)
      → 微信 code2session → openid
      → 查 t_user（不存在则建档，audit_status=0）
      → 查 t_user_role → 计算可进入的入口
      → Redis SET auth:token:{token} = {userId, roles}，TTL 7 天
  ← { token, realNameStatus, entries[] }

未实名 → POST /auth/realname → submitRealName()
      → 写 t_user 实名字段、audit_status=0
      → AuditService.record(REAL_NAME, userId) 落 SUBMIT 流水(pending=1)
  → 组织管理员在管理端看到待审
  → POST /auth/realname/review → reviewRealName()
      → RealNameHandler.migrate() 迁移 t_user.audit_status
      → AuditService 落 APPROVE / REJECT 流水，pending=0
  → 通过后：计入台账、参与排行与考核

【管理员注册与授权】（F20）
账号首次进入管理端 → assertEntryAllowed(userId, ADMIN)
  → 无 role ∈ {3,4,5} → 返回 3002
  → 前端引导至 packageAdmin/pages/apply
  → POST /admin/apply → AdminApplyService.submit()
      → 校验无有效申请 → 写 t_admin_application(status=0)
      → AuditService.record(ADMIN_APPLY, applyId) 落流水
  → 平台管理员在管理端审核 → POST /admin/apply/review
      → AdminApplyHandler.migrate() 迁移 t_admin_application.status
      → 通过时向 t_user_role 插入 role=3 或 4
      → AuditService 落流水，pending=0
  → 该账号此后可进入管理端
```

### 6.2 数据范围拦截（F4、N1）

```
AuthInterceptor：解析 Token → 注入 LoginContext{operatorId, roles}，controller 不接受客户端传入身份
DataScopeAspect：对所有标注 @DataScoped 的管理类方法
  → OrgService.resolveVisibleOrgIds(operatorId)
      → 取操作员 org_id → 查 t_org.path
      → SELECT id FROM t_org WHERE path LIKE CONCAT(#{path}, '%')
      → 平台管理员跳过此步
  → 把可见组织集合写入上下文，后续 SQL 强制带 org_id IN (...)
```

### 6.3 学习与学时（F7、F8、N4）

```
播放器每 15 秒心跳 → POST /learn/heartbeat
  → LearnService.reportHeartbeat(userId, cmd)

  【校验链】
   1. t_learn_heartbeat 存在 (sessionId, clientSeq) → 返回该次 acceptedSec（幂等）
   2. clientSeq ≤ t_learn_session.last_seq → reject_reason=OUT_OF_ORDER，acceptedSec=0
   3. now - last_end_at < 5s 或本分钟心跳数 > 12 → reject_reason=RATE_LIMITED，返回 4003
   4. deltaSec > 300 → 按 300 计，reject_reason=OVER_DELTA
   5. 有效秒数计算（失焦口径）：
        unfocusedSec = deltaSec - focusedSec
        session.unfocused_sec += unfocusedSec
        仅当 session.unfocused_sec > 600 时，超出 600 的部分从本条扣除
        → 即「失焦累计超过 10 分钟的部分不计入」，与 spec F8 一致
      form=1 的图文类无播放窗口语义，客户端固定上报 focusedSec = deltaSec，
      因此图文类不产生失焦扣除

  【写入，同一事务】
   → upsert t_learn_heartbeat（幂等键）
   → update t_learn_session（last_seq、last_end_at、unfocused_sec、valid_sec）
   → upsert t_learn_record（进度、累计有效秒数）
   → upsert t_credit_daily（minutes += validSec/60，封顶 120）
   → CreditService.accrue(..., t_credit_daily.id, minutes)
        → 依据 uk_credit_source 判定：分录不存在则 INSERT，存在则 UPDATE minutes 与 credit
  ← HeartbeatResult{ acceptedSec, progressPct, todayMinutes, todayCredit, accepted }

断网时：客户端本地队列缓存心跳，恢复后按 clientSeq 顺序补传，source=2
```

**三层防刷**：会话内序号单调（防重放）、单位时间心跳限流（防短间隔连发）、单日封顶（防长时间挂机）。客户端只报「我学了多少秒」，学时数额完全由服务端算。

### 6.4 答题与排行榜（F10、F11）

```
取卷 → POST /exam/paper/{id} → ExamService.getPaper()（不下发答案）
提交 → POST /exam/submit → ExamService.submit()
    → 服务端比对答案判分 → 写 t_exam_record
    → RankingService.addScore(userId, orgId, EXAM_SCORE, score)
        → Redis ZINCRBY rank:USER:{orgId}:{periodType}:{periodKey} {score} {userId}
        → Redis ZINCRBY rank:ORG:{periodType}:{periodKey} {score} {orgId}
    → 定时任务（每日 02:00）把两个 ZSET 落成 t_ranking_snapshot

用户查询：ZREVRANGE 取前 20 + ZREVRANK 取本人名次
管理员查询：不走 Redis，按 orgId 与可见组织范围查 t_ranking_snapshot 聚合
```

### 6.5 看板与台账导出（F19、F8、N2）

```
DashboardService.overview()
  → 一次聚合 SQL 出五项指标
  → 在册人数口径：identity_type IN (1,2,3) AND audit_status = 1
  → 活跃度 = 有学习记录人数 ÷ 在册人数，分母为 0 时返回 0

CreditService.exportOrgLedger()
  → 按可见组织范围分页扫 t_credit 汇总（user × period）
  → EasyExcel 写 xlsx 到对象存储
  → 写 t_sensitive_access_log(biz_type=USER_MOBILE, scene=LEDGER_EXPORT)（N2）
  → 返回带 30 分钟时效签名的下载链接
```

### 6.6 资源审核（F9、F21）

```
POST /resource/save → ResourceService.saveResource()
  → 校验字段（含字幕必填规则）→ 写 t_resource，status=待审核
  → AuditService.record(RESOURCE, resourceId, summary, operatorId)   ← 唯一写入路径
  ← 返回 resourceId

POST /audit/review → AuditService.review(operatorId, RESOURCE, resourceId, pass, remark)
  → AuditableHandler 查 ResourceAuditHandler（party-content 实现并注册）
  → ResourceAuditHandler.migrate(resourceId, APPROVE/REJECT, remark)
      → 迁移 t_resource.status：待审核 → 已上架 / 草稿
  → AuditService 落流水并把 pending 置 0
  ← 完成

下架：changeStatus(TAKE_DOWN) → status=已下架 → AuditService.record(TAKE_DOWN)
```

资源状态机单向，每次迁移必有流水，对应 AC22「每条审核操作可查到操作人、时间与结果」。

---

## 七、文件组织

### 7.1 服务端

```
party-server/
├── pom.xml                                  父 POM，锁定 Spring Boot / MyBatis-Plus / Redis / EasyExcel 版本
├── party-common/
│   ├── pom.xml
│   └── src/main/java/com/hongmai/common/
│       ├── web/R.java                        统一响应体
│       ├── web/PageQuery.java                分页入参
│       ├── web/PageResult.java               分页出参
│       ├── web/LoginContext.java             当前登录上下文（operatorId、roles、可见组织）
│       ├── exception/BizException.java       业务异常
│       ├── exception/ErrorCode.java          错误码枚举（分段见 5.1）
│       ├── exception/GlobalExceptionHandler.java  统一异常转 R
│       ├── crypto/CryptoUtil.java            AES 加解密与手机号掩码（N2）
│       ├── crypto/MobileCipherHandler.java   MyBatis 字段加密 TypeHandler
│       ├── crypto/MaskUtil.java              末四位脱敏工具
│       ├── enums/                            全部业务枚举（身份类型、资源形态、状态机、审核类型等）
│       ├── util/OrgPathUtil.java             组织路径拼装与解析
│       └── util/DateUtil.java                时区与统计日工具（Asia/Shanghai）
├── party-audit/
│   └── src/main/java/com/hongmai/audit/
│       ├── controller/AuditController.java      审核接口
│       ├── service/AuditService.java
│       ├── service/impl/AuditServiceImpl.java
│       ├── domain/AuditableHandler.java         可审对象能力接口（依赖倒置点）
│       ├── domain/AuditBizType.java             可审对象类型枚举
│       ├── domain/AuditAction.java              审核动作枚举
│       ├── domain/AuditLog.java                 流水实体
│       └── mapper/AuditLogMapper.java
├── party-auth/
│   └── src/main/java/com/hongmai/auth/
│       ├── controller/AuthController.java       登录、实名提交与审核
│       ├── controller/AdminApplyController.java 管理员申请与审核
│       ├── service/AuthService.java
│       ├── service/AdminApplyService.java
│       ├── service/impl/AuthServiceImpl.java
│       ├── service/impl/AdminApplyServiceImpl.java
│       ├── service/impl/RealNameAuditHandler.java 实现 AuditableHandler，迁移 t_user.audit_status
│       ├── service/impl/AdminApplyAuditHandler.java 实现 AuditableHandler，迁移 t_admin_application.status
│       ├── domain/User.java
│       ├── domain/UserRole.java
│       ├── domain/AdminApplication.java
│       ├── dto/RealNameCmd.java
│       ├── dto/AdminApplyCmd.java
│       ├── vo/LoginResult.java
│       ├── vo/RealNameApplyVO.java
│       ├── vo/AdminApplyVO.java
│       ├── mapper/UserMapper.java
│       ├── mapper/UserRoleMapper.java
│       └── mapper/AdminApplicationMapper.java
├── party-org/
│   └── src/main/java/com/hongmai/org/
│       ├── controller/RosterController.java
│       ├── service/OrgService.java
│       ├── service/RosterService.java
│       ├── service/impl/OrgServiceImpl.java
│       ├── service/impl/RosterServiceImpl.java
│       ├── domain/Org.java
│       ├── dto/RosterQuery.java
│       ├── vo/RosterItemVO.java
│       ├── vo/EthnicityStatVO.java
│       ├── mapper/OrgMapper.java
│       └── mapper/RosterMapper.java             名册与民族统计的聚合 SQL
├── party-content/
│   └── src/main/java/com/hongmai/content/
│       ├── controller/ResourceController.java   资源 CRUD、检索、详情、上下架
│       ├── service/ResourceService.java
│       ├── service/impl/ResourceServiceImpl.java
│       ├── service/impl/ResourceAuditHandler.java 实现 AuditableHandler，迁移 t_resource.status
│       ├── domain/Resource.java
│       ├── domain/ResourceStatus.java
│       ├── dto/ResourceSaveCmd.java
│       ├── dto/ResourceFilter.java
│       ├── vo/ResourceCardVO.java
│       ├── vo/ResourceDetailVO.java
│       └── mapper/ResourceMapper.java
├── party-learn/
│   └── src/main/java/com/hongmai/learn/
│       ├── controller/LearnController.java      心跳上报、学习记录
│       ├── controller/CreditController.java     学时概览、台账导出、目标学时设置
│       ├── service/LearnService.java
│       ├── service/CreditService.java
│       ├── service/CreditTargetService.java
│       ├── service/impl/LearnServiceImpl.java
│       ├── service/impl/CreditServiceImpl.java
│       ├── service/impl/CreditTargetServiceImpl.java
│       ├── domain/CreditRule.java              折算规则常量：45 分钟/学时、单日 120 分钟封顶
│       ├── domain/HeartbeatValidator.java      心跳校验链（幂等、序号、限流、增量上限）
│       ├── domain/FocusCalculator.java         失焦口径计算（10 分钟阈值）
│       ├── domain/LearnSession.java
│       ├── domain/LearnHeartbeat.java
│       ├── domain/LearnRecord.java
│       ├── domain/CreditDaily.java
│       ├── domain/Credit.java
│       ├── domain/CreditTarget.java
│       ├── dto/HeartbeatCmd.java
│       ├── vo/HeartbeatResult.java
│       ├── vo/LearnRecordVO.java
│       ├── vo/CreditOverviewVO.java
│       ├── vo/ExportResult.java
│       ├── mapper/LearnSessionMapper.java
│       ├── mapper/LearnHeartbeatMapper.java
│       ├── mapper/LearnRecordMapper.java
│       ├── mapper/CreditDailyMapper.java
│       ├── mapper/CreditMapper.java
│       ├── mapper/CreditTargetMapper.java
│       ├── mapper/LedgerExportMapper.java       台账汇总聚合 SQL
│       └── export/LedgerExcelWriter.java        EasyExcel 写出器
├── party-exam/
│   └── src/main/java/com/hongmai/exam/
│       ├── controller/ExamController.java       取卷、提交
│       ├── controller/RankingController.java    榜单查询
│       ├── controller/DashboardController.java  管理端看板
│       ├── service/ExamService.java
│       ├── service/RankingService.java
│       ├── service/DashboardService.java
│       ├── service/impl/ExamServiceImpl.java
│       ├── service/impl/RankingServiceImpl.java
│       ├── service/impl/DashboardServiceImpl.java
│       ├── domain/ExamPaper.java
│       ├── domain/ExamRecord.java
│       ├── domain/RankingSnapshot.java
│       ├── domain/RankingMetric.java
│       ├── dto/RankingQuery.java
│       ├── vo/PaperVO.java
│       ├── vo/QuestionVO.java
│       ├── vo/ExamResultVO.java
│       ├── vo/AnswerDetailVO.java
│       ├── vo/RankingVO.java
│       ├── vo/RankingItemVO.java
│       ├── vo/DashboardVO.java
│       ├── vo/ScoreBucketVO.java
│       ├── mapper/ExamPaperMapper.java
│       ├── mapper/ExamRecordMapper.java
│       ├── mapper/RankingSnapshotMapper.java
│       └── mapper/DashboardMapper.java          五项指标聚合 SQL
└── party-boot/
    ├── pom.xml
    └── src/main/
        ├── java/com/hongmai/boot/
        │   ├── BootApplication.java
        │   ├── config/WebMvcConfig.java         注册拦截器与切面
        │   ├── config/MybatisPlusConfig.java    分页插件、逻辑删除、TypeHandler 注册
        │   ├── config/RedisConfig.java
        │   ├── config/ObjectStorageConfig.java
        │   ├── interceptor/AuthInterceptor.java  Token 解析 → 注入 LoginContext
        │   ├── aspect/DataScopeAspect.java       数据范围校验切面
        │   └── job/RankingSnapshotJob.java       每日 02:00 排行榜快照
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            └── mapper/                          自定义 SQL XML，按模块分子目录
                ├── org/RosterMapper.xml
                ├── learn/LedgerExportMapper.xml
                └── exam/DashboardMapper.xml
```

**测试约定**：每个模块下建 `src/test/java/com/hongmai/{module}/`，单元测试命名 `XxxServiceTest`；跨模块关键链路的集成测试置于 party-boot 的 `src/test/java/com/hongmai/boot/it/`，至少覆盖三条：心跳幂等与补传、审核状态机迁移、数据范围越权拦截。

### 7.2 小程序端

```
miniprogram/
├── app.js / app.json / app.wxss          app.json 声明三个页面根与分包
├── pages/                                主包：仅入口与登录
│   ├── entry/                            双入口选择
│   └── auth/                             微信授权 + 实名填写
├── packageUser/                          用户端分包
│   └── pages/
│       ├── home/                         首页（推荐与最近学习）
│       ├── learn/                        资源列表与检索筛选
│       ├── resource/                     资源详情与播放器
│       ├── credit/                       学时与达标进度
│       ├── exam/                         答题
│       ├── ranking/                      排行榜
│       └── profile/                      我的 / 学习记录
├── packageAdmin/                         管理端分包
│   └── pages/
│       ├── home/                         管理端首页（待办入口）
│       ├── apply/                        管理员注册申请
│       ├── roster/                       党员台账
│       ├── ethnicity/                    少数民族党员统计
│       ├── dashboard/                    学习看板
│       ├── review/                       审核中心（实名 / 内容）
│       └── export/                       学时台账导出
├── components/
│   ├── learn-card/                       资源卡片
│   ├── media-player/                     图文 / 视频 / 全景播放器
│   ├── subtitle-overlay/                 中文字幕层（承 AC28，字幕语言固定不随界面语言变）
│   ├── empty-state/                      空状态
│   └── stat-tile/                        看板指标块
├── styles/
│   └── tokens.wxss                       设计令牌：正文字号 16px 起、色板、间距（承 N5、N6）
├── utils/
│   ├── request.js                        统一请求封装，拦截 code=2001 触发重登
│   ├── auth.js                           登录态与入口鉴权
│   ├── heartbeat.js                      播放心跳上报 + 失焦统计 + 断网本地队列补传
│   ├── mask.js                           手机号脱敏
│   └── format.js                         日期与学时格式化
├── i18n/
│   ├── index.js                          文案读取入口
│   └── zh-Hans.js                        V1 唯一语言包；V1 起所有文案必须从此引用
└── config/env.js                         环境配置（开发 / 生产域名）
```

**N9 的落地方式**：从 V1 第一天起，WXML 中不允许硬编码任何文案，必须走 `i18n/index.js` 的 key。V3.3 接入藏语、彝语时只需新增语言包，无需回溯改页面。样式规范落在 `styles/tokens.wxss`，正文字号下限 16px 作为令牌固化，避免逐页手写（承 N5、N6）。

---

## 八、功能需求到模块的映射

| 需求 | 归属模块 | 版本 |
|---|---|---|
| F1 双入口与身份隔离 | party-auth | V1 |
| F2 用户实名注册与组织审核 | party-auth | V1 |
| F3 角色与权限 | party-auth | V1 |
| F4 组织架构与数据范围 | party-org | V1 |
| F5 学习资源库浏览与检索 | party-content | V1 |
| F6 内容分类体系 | party-content | V1 |
| F7 在线学习与进度跟踪 | party-learn | V1 |
| F8 学时统计与台账 | party-learn | V1 |
| F9 学习资源内容管理 | party-content | V1 |
| F10 在线答题与自动判分 | party-exam | V1 |
| F11 排行榜 | party-exam | V1 |
| F12 积极分子量化考核 | party-assess | V2.1 |
| F13 考核指标配置 | party-assess | V2.1 |
| F14 量化材料提交 | party-assess | V2.1 |
| F15 材料审核 | party-assess + party-audit | V2.1 |
| F16 量化可视化与细节追溯 | party-assess | V2.1 |
| F17 组织评定项打分 | party-assess | V2.1 |
| F18 党员台账与少数民族党员统计 | party-org | V1 |
| F19 学习情况看板 | party-exam | V1 |
| F20 管理员注册与审核 | party-auth | V1 |
| F21 内容审核 | party-audit + party-content | V1 |
| F22 党建活动组织与档案 | party-activity | V2.2 |
| F23 学习互动与留言 | party-interact | V3.1 |
| F24 消息通知 | party-notify | V3.2 |
| F25 多语言界面 | party-i18n + 小程序 i18n 目录 | V3.3 |
| N1 数据安全 | party-auth + party-boot 拦截器与切面 | V1 |
| N2 数据合规 | party-common 加密组件（CryptoUtil/MobileCipherHandler）+ t_sensitive_access_log | V1 |
| N3 性能 | 索引设计 + Redis 排行榜 + 分页上限 | V1 |
| N4 弱网与离线可用 | 小程序 utils/heartbeat.js + t_learn_heartbeat 幂等 | V1 |
| N5 兼容性 | 小程序 styles/tokens.wxss 与页面布局规范 | V1 |
| N6 阅读友好 | styles/tokens.wxss 字号令牌 + t_resource.subtitle_url + components/subtitle-overlay | V1 |
| N7 内容合规 | party-audit | V1 |
| N8 可配置性 | party-assess（配置表驱动） | V2.1 |
| N9 多语言一致性 | 小程序 i18n 目录 + party-i18n | V1 起约束 |

无遗漏需求。

---

## 九、V1 实现顺序与验收

按模块依赖顺序推进，每步完成即跑该模块的单元测试：

1. **party-common** —— 响应体、错误码、异常、枚举、加密与脱敏、组织路径与日期工具
2. **infra** —— MySQL 建表脚本、MyBatis-Plus 配置、Redis 配置、对象存储、AuthInterceptor、DataScopeAspect
3. **party-audit** —— 流水表、待审队列、能力接口（先建骨架，供后续模块实现）
4. **party-auth** —— 登录、实名提交与审核、Token 与入口鉴权、管理员申请与审核
5. **party-org** —— 组织树、名册、民族统计、数据范围解析
6. **party-content** —— 资源 CRUD、检索与详情、状态机与 ResourceAuditHandler
7. **party-learn** —— 心跳校验链、失焦口径、学时折算与入账、目标学时、台账导出
8. **party-exam** —— 判分、排行榜、五项看板
9. **小程序端** —— 主包登录实名 → 用户端分包 → 管理端分包
10. **联调与验收** —— 逐条跑验收标准

**V1 验收范围**：AC1-AC29 与 AC35。**AC30 对应 N8（九项指标权重可配置），归属 V2.1，不在 V1 验收范围内**——这是 spec 版本划分的直接结果。

---

## 十、技术债声明

| 债 | 影响 | 何时偿还 |
|---|---|---|
| 不引入消息队列，异步任务用 Spring Task 单机调度 | 多实例部署时定时任务会重复执行 | 服务端扩到 2 个实例之前换 XXL-Job |
| 排行榜快照每日生成一次 | 不能查询「某小时内」的历史排名 | 出现该需求时改为事件驱动落库 |
| 资源检索用 MySQL `LIKE` | 资源量超过万级时检索变慢 | 资源量破万后引入全文索引或 ES |
| 对象存储不做 CDN 加速 | 偏远地区首屏图片加载偏慢，与 N3 的 2 秒目标有张力 | 上线后按实测数据决定 |
| 单组织容量按 5000 人设计 | 超过后看板聚合查询退化 | 突破时按组织分片或预计算 |
| 「每年脱产 40 学时」不做采集 | 该类学时只能线下统计，线上达标率不含此项 | 需要线上化时另立需求 |
| V2+ 数据表本期不建 | 后续版本需执行建表迁移 | 各版本开发前执行对应 migration |
| i18n 表在 V3.3 才建，V1 只落地文案码机制与文案集中管理 | V1 期间服务端错误提示只有中文 | V3.3 接入时补 messageKey 的多语言资源 |
| 组织移动时整棵子树重写 path，未做异步化 | 大规模组织调整会阻塞 | 组织数量上千后改为异步重建 |
