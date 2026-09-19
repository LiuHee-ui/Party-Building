-- =============================================================================
-- 开发种子数据（仅用于本地联调，不可用于生产）
-- 内容为示例，来源为公开党史知识，不含任何真实党员个人信息
-- =============================================================================

USE `hongmai_party`;

-- -----------------------------------------------------------------------------
-- 学习资源：覆盖三种形态，用于验证「VR 不给播放地址」「视频必须有字幕」两条规则
-- -----------------------------------------------------------------------------
INSERT INTO `t_resource`
(`id`, `title`, `summary`, `form`, `theme`, `vr_theme`, `vr_form`, `content_url`,
 `duration_sec`, `subtitle_url`, `subtitle_lang`, `status`, `sort_no`, `created_by`,
 `created_at`, `updated_at`, `deleted`)
VALUES
-- 图文：无字幕、无时长
(1, '红军长征在四川', '红军长征途经四川的民族地区，留下了大量革命遗迹与动人故事。',
 1, 1, NULL, NULL, 'https://example.invalid/resource/1.html',
 NULL, NULL, 'zh-Hans', 2, 100, 1, NOW(), NOW(), 0),

-- 视频：必须有字幕与时长
(2, '飞夺泸定桥', '1935 年 5 月，红军先头部队在泸定桥上创造了军事史上的奇迹。',
 2, 1, NULL, NULL, 'https://example.invalid/resource/2.mp4',
 720, 'https://example.invalid/resource/2.zh.vtt', 'zh-Hans', 2, 90, 1, NOW(), NOW(), 0),

-- 全景：同样要求字幕
(3, '彝海结盟纪念地全景', '走进彝海结盟纪念地，了解民族团结的历史见证。',
 3, 1, NULL, NULL, 'https://example.invalid/resource/3.jpg',
 300, 'https://example.invalid/resource/3.zh.vtt', 'zh-Hans', 2, 80, 1, NOW(), NOW(), 0),

-- VR：不提供站内播放地址，因此 content_url 与 subtitle_url 均为空
(4, 'VR 血战湘江', '沉浸式体验湘江战役，需前往 VR 党建工作站完成。',
 4, 1, 1, 1, NULL,
 NULL, NULL, 'zh-Hans', 2, 70, 1, NOW(), NOW(), 0);

-- -----------------------------------------------------------------------------
-- 试卷：3 题，单选 + 多选 + 判断，总分 100
-- 标准答案只存在这里，不会下发到客户端
-- -----------------------------------------------------------------------------
INSERT INTO `t_exam_paper`
(`id`, `title`, `resource_id`, `question_ids_json`, `total_score`, `status`, `created_at`, `updated_at`, `deleted`)
VALUES
(1, '党史基础知识测验', 1,
 '[{"id":1,"type":1,"score":40,"stem":"中国共产党第一次全国代表大会召开于哪一年？","options":[{"key":"A","text":"1919 年"},{"key":"B","text":"1921 年"},{"key":"C","text":"1927 年"},{"key":"D","text":"1935 年"}],"correctOptions":["B"]},'
 '{"id":2,"type":2,"score":30,"stem":"下列哪些属于长征精神的内涵？（多选）","options":[{"key":"A","text":"坚定革命理想和信念"},{"key":"B","text":"独立自主、实事求是"},{"key":"C","text":"个人英雄主义"},{"key":"D","text":"顾全大局、严守纪律、紧密团结"}],"correctOptions":["A","B","D"]},'
 '{"id":3,"type":3,"score":30,"stem":"彝海结盟是红军长征途中民族团结的历史见证。","options":[{"key":"A","text":"正确"},{"key":"B","text":"错误"}],"correctOptions":["A"]}]',
 100, 1, NOW(), NOW(), 0);
