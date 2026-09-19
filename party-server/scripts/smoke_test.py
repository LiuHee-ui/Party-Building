#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
端到端联调冒烟测试。

跑通 spec 的核心链路，验证「后端 7 个模块在真实 MySQL + Redis 上能协同工作」——
这是单元测试覆盖不到的：单测各自 mock 掉依赖，无法发现 SQL 语法、字段映射、
事务边界、Redis 键格式这类问题。

链路：
  1. 开发免微信登录（建档）
  2. 补齐实名与组织（直接改库，模拟已通过审核）
  3. 重新登录（角色与组织随 Token 下发，必须重登才生效）
  4. 用户端资源列表 / VR 详情不给播放地址
  5. 管理端新建资源 → 进入待审队列 → 审核通过 → 用户端可见
  6. 学习心跳 × N → 学时日累计与流水入账
  7. 学时概览（自然年 + 五年期）
  8. 交卷判分（服务端判分，重复提交被拒）
  9. 排行榜与看板

用法：python scripts/smoke_test.py
"""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
MYSQL = ["mysql", "-h127.0.0.1", "-uroot", "-p***REMOVED***", "-N", "--default-character-set=utf8mb4",
         "-e"]
DB = "hongmai_party"

MEMBER_OPENID = "dev-smoke-member"
ADMIN_OPENID = "dev-smoke-admin"

passed = 0
failed = 0


def sql(statement):
    proc = subprocess.run(MYSQL + [statement], capture_output=True, text=True, encoding="utf-8")
    if proc.returncode != 0:
        raise RuntimeError("SQL 失败: " + proc.stderr.strip())
    return proc.stdout.strip()


def call(method, path, token=None, body=None, query=None):
    url = BASE + path
    if query:
        url += "?" + "&".join(f"{k}={v}" for k, v in query.items())
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": e.code, "message": "HTTP " + str(e.code), "raw": e.read().decode("utf-8", "ignore")}
    except Exception as e:
        return {"code": -1, "message": str(e)}


def check(name, condition, detail=""):
    global passed, failed
    if condition:
        passed += 1
        print(f"  [通过] {name}" + (f"  ({detail})" if detail else ""))
    else:
        failed += 1
        print(f"  [失败] {name}" + (f"  -> {detail}" if detail else ""))


def login(openid):
    r = call("POST", "/dev/auth/login", query={"openId": openid})
    if r.get("code") != 0:
        raise RuntimeError(f"登录失败 openid={openid}: {r}")
    return r["data"]["token"], r["data"]


def section(title):
    print("\n" + "=" * 72)
    print(title)
    print("=" * 72)


# ---------------------------------------------------------------------------
section("步骤 1  开发免微信登录（首次建档）")
member_token, member_login = login(MEMBER_OPENID)
check("登录成功并返回 Token", bool(member_token))
check("首次建档 firstTime=true", member_login.get("firstTime") is True)
check("实名状态为「未提交」(9)", member_login.get("realNameStatus") == 9,
      f"实际 {member_login.get('realNameStatus')}")
check("可进入入口仅用户端", [e["code"] for e in member_login.get("entries", [])] == ["USER"],
      str(member_login.get("entries")))

member_id = sql(f"SELECT id FROM {DB}.t_user WHERE open_id='{MEMBER_OPENID}'")
print(f"  → 建档 userId={member_id}")

# ---------------------------------------------------------------------------
section("步骤 2  补齐实名与组织（模拟实名审核已通过）")
sql(f"""UPDATE {DB}.t_user SET real_name='次仁卓玛', mobile_tail='8000',
        mobile_hash=REPEAT('a',64), org_id=3, identity_type=3, ethnicity='藏族',
        audit_status=1 WHERE id={member_id}""")
sql(f"""INSERT IGNORE INTO {DB}.t_user_role(user_id, role, created_at, updated_at, deleted)
        VALUES ({member_id}, 3, NOW(), NOW(), 0)""")
print("  已设置：组织=3（第一村党支部）、身份=积极分子、民族=藏族、角色=组织管理员、实名已通过")

# ---------------------------------------------------------------------------
section("步骤 3  重新登录（角色与组织随 Token 下发）")
member_token, member_login = login(MEMBER_OPENID)
entries = [e["code"] for e in member_login.get("entries", [])]
check("实名状态变为「已通过」(1)", member_login.get("realNameStatus") == 1)
check("可进入两个入口", sorted(entries) == ["ADMIN", "USER"], str(entries))

admin_token, _ = login(ADMIN_OPENID)
admin_id = sql(f"SELECT id FROM {DB}.t_user WHERE open_id='{ADMIN_OPENID}'")
sql(f"UPDATE {DB}.t_user SET real_name='平台管理员', mobile_tail='0001', mobile_hash=REPEAT('b',64), "
    f"org_id=1, identity_type=1, ethnicity='汉族', audit_status=1 WHERE id={admin_id}")
sql(f"""INSERT IGNORE INTO {DB}.t_user_role(user_id, role, created_at, updated_at, deleted)
        VALUES ({admin_id}, 5, NOW(), NOW(), 0),
               ({admin_id}, 3, NOW(), NOW(), 0),
               ({member_id}, 3, NOW(), NOW(), 0)""")
admin_token, _ = login(ADMIN_OPENID)
print(f"  → 管理员 userId={admin_id}，已授予平台管理员角色")

# ---------------------------------------------------------------------------
section("步骤 4  用户端资源列表与 VR 详情")
r = call("GET", "/resource/page", token=member_token, query={"pageNo": 1, "pageSize": 20})
check("资源列表返回成功", r.get("code") == 0, str(r.get("message")))
total_before = r["data"]["total"]
check("种子资源可见（4 条已上架）", total_before == 4, f"实际 {total_before}")

r = call("GET", "/resource/4", token=member_token)
check("VR 详情返回成功", r.get("code") == 0)
check("VR 详情不返回播放地址（spec F5）", r["data"].get("contentUrl") is None,
      f"实际 {r['data'].get('contentUrl')}")
check("VR 详情给出到站引导", bool(r["data"].get("experienceHint")))
check("VR 主题标签正确", r["data"].get("vrThemeLabel") == "革命战争",
      str(r["data"].get("vrThemeLabel")))

r = call("GET", "/resource/2", token=member_token)
check("视频详情返回中文字幕（N6）", bool(r["data"].get("subtitleUrl")),
      str(r["data"].get("subtitleUrl")))
check("字幕后缀为 zh-Hans", r["data"].get("subtitleLang") == "zh-Hans")

# ---------------------------------------------------------------------------
section("步骤 5  资源审核闭环（校验字幕必填 + 状态机）")
bad = call("POST", "/resource/save", token=member_token, body={
    "title": "缺字幕的视频", "form": 2, "theme": 1,
    "contentUrl": "https://example.invalid/x.mp4", "durationSec": 300})
check("视频缺字幕被拒（4006 参数校验）", bad.get("code") == 1000, f"code={bad.get('code')} msg={bad.get('message')}")

vr_bad = call("POST", "/resource/save", token=member_token, body={
    "title": "带播放地址的 VR", "form": 4, "theme": 1, "vrTheme": 1, "vrForm": 1,
    "contentUrl": "https://example.invalid/vr.mp4"})
check("VR 带播放地址被拒（spec F5）", vr_bad.get("code") == 1000)

good = call("POST", "/resource/save", token=member_token, body={
    "title": "民族团结进步故事（联调新建）", "summary": "联调测试资源",
    "form": 2, "theme": 3, "contentUrl": "https://example.invalid/new.mp4",
    "subtitleUrl": "https://example.invalid/new.zh.vtt", "durationSec": 600})
check("合规资源保存成功", good.get("code") == 0, str(good.get("message")))
new_resource_id = good.get("data")
check("返回新资源 id", isinstance(new_resource_id, int), str(new_resource_id))

r = call("GET", "/resource/page", token=member_token, query={"pageNo": 1, "pageSize": 20})
check("待审资源不出现在用户端列表", r["data"]["total"] == total_before,
      f"期望 {total_before} 实际 {r['data']['total']}")

r = call("GET", "/audit/pending", token=member_token, query={"pageNo": 1, "pageSize": 20})
check("待审队列查询成功", r.get("code") == 0, str(r.get("message")))
pending_total = r["data"]["total"] if r.get("code") == 0 else -1
check("待审队列出现该资源", pending_total >= 1, f"待审 {pending_total} 条")

r = call("POST", "/audit/review", token=member_token, body={
    "bizType": 1, "bizId": new_resource_id, "pass": True})
check("审核通过成功", r.get("code") == 0, str(r.get("message")))

r = call("POST", "/audit/review", token=member_token, body={
    "bizType": 1, "bizId": new_resource_id, "pass": True})
check("重复审核被拒（4004 状态机）", r.get("code") == 4004, f"code={r.get('code')}")

r = call("GET", "/resource/page", token=member_token, query={"pageNo": 1, "pageSize": 20})
check("通过后用户端可见（5 条）", r["data"]["total"] == total_before + 1,
      f"期望 {total_before + 1} 实际 {r['data']['total']}")

r = call("POST", "/resource/status", token=member_token,
         query={"resourceId": new_resource_id, "target": 2})
check("上架不能走状态变更接口（须走审核）", r.get("code") == 4004, f"code={r.get('code')}")

r = call("POST", "/resource/status", token=member_token,
         query={"resourceId": new_resource_id, "target": 3})
check("下架成功", r.get("code") == 0, str(r.get("message")))

# ---------------------------------------------------------------------------
section("步骤 6  学习心跳与学时入账")
session_id = "smoke-session-1"
hearts = []
for seq in range(1, 5):
    # 服务端有 3 秒最小接受间隔（防脚本刷时长），这里必须真实等待，
    # 否则心跳会被判 RATE_LIMITED —— 那是服务端正确行为，不是缺陷
    if seq > 1:
        time.sleep(3.2)
    r = call("POST", "/learn/heartbeat", token=member_token, body={
        "sessionId": session_id, "resourceId": 2, "clientSeq": seq,
        "deltaSec": 60, "focusedSec": 60, "positionSec": seq * 60, "durationSec": 720})
    if r.get("code") != 0:
        check(f"心跳 #{seq} 失败", False, str(r))
        break
    hearts.append(r["data"])

check("4 次心跳全部成功", len(hearts) == 4, f"成功 {len(hearts)} 次")
if hearts:
    last = hearts[-1]
    check("心跳被接受", all(h.get("accepted") for h in hearts),
          str([h.get("rejectReason") for h in hearts]))
    check("单次计入 60 秒（增量上限）", hearts[0].get("acceptedSec") == 60,
          str(hearts[0].get("acceptedSec")))
    check("当日累计 4 分钟", last.get("todayMinutes") == 4, str(last.get("todayMinutes")))
    check("当日学时 4/45 ≈ 0.09", str(last.get("todayCredit")) == "0.09", str(last.get("todayCredit")))

r = call("POST", "/learn/heartbeat", token=member_token, body={
    "sessionId": session_id, "resourceId": 2, "clientSeq": 4,
    "deltaSec": 60, "focusedSec": 60, "positionSec": 300, "durationSec": 720})
check("重复序号被判重放（幂等）", r.get("code") == 0 and r["data"].get("accepted") is False,
      f"accepted={r.get('data', {}).get('accepted')} reason={r.get('data', {}).get('rejectReason')}")

time.sleep(3.2)
r = call("POST", "/learn/heartbeat", token=member_token, body={
    "sessionId": session_id, "resourceId": 2, "clientSeq": 5,
    "deltaSec": 3600, "focusedSec": 3600, "positionSec": 600, "durationSec": 720})
check("超长间隔按 60 秒截断（不丢弃）", r["data"].get("acceptedSec") == 60,
      f"acceptedSec={r['data'].get('acceptedSec')} reason={r['data'].get('rejectReason')}")

db_minutes = sql(f"SELECT IFNULL(SUM(minutes),0) FROM {DB}.t_credit_daily WHERE user_id={member_id}")
check("学时日累计已落库（5 分钟）", int(db_minutes) == 5, f"实际 {db_minutes} 分钟")

db_credit = sql(f"SELECT IFNULL(SUM(credit),0) FROM {DB}.t_credit WHERE user_id={member_id}")
check("学时流水已入账", float(db_credit) > 0, f"实际 {db_credit} 学时")

db_periods = sql(f"SELECT COUNT(DISTINCT period_key) FROM {DB}.t_credit WHERE user_id={member_id}")
check("自然年与五年期两本账都已写", int(db_periods) == 2, f"实际 {db_periods} 个周期键")

# ---------------------------------------------------------------------------
section("步骤 7  学时概览")
r = call("GET", "/learn/credit/overview", token=member_token)
check("学时概览返回成功", r.get("code") == 0, str(r.get("message")))
if r.get("code") == 0:
    d = r["data"]
    check("返回自然年目标（积极分子 32）", str(d.get("yearTarget")) == "32", str(d.get("yearTarget")))
    check("返回五年期目标（积极分子 160）", str(d.get("fiveYearTarget")) == "160",
          str(d.get("fiveYearTarget")))
    check("累计分钟 > 0", (d.get("yearMinutes") or 0) > 0, str(d.get("yearMinutes")))

# ---------------------------------------------------------------------------
section("步骤 8  答题判分（服务端判分 + 防重复提交）")
r = call("GET", "/exam/paper/1", token=member_token)
check("取卷成功", r.get("code") == 0, str(r.get("message")))
if r.get("code") == 0:
    qs = r["data"]["questions"]
    check("返回 3 道题", len(qs) == 3, str(len(qs)))
    leaked = [q for q in qs if "correctOptions" in q or "correct" in q]
    check("返回体不含标准答案", not leaked, str(leaked)[:200])

r = call("POST", "/exam/paper/1/submit", token=member_token,
         body={"1": ["B"], "2": ["A", "B", "D"], "3": ["A"]})
# 断言数值而不是字符串：BigDecimal 的序列化形式可能是 100.00 也可能被规范化成 100.0，
# 断言字符串表示会让测试变脆
check("全对得 100 分", r.get("code") == 0 and float(r["data"].get("score", -1)) == 100.0,
      f"code={r.get('code')} score={r.get('data', {}).get('score')}")
if r.get("code") == 0:
    check("判定及格", r["data"].get("passed") is True)
    check("交卷后回显标准答案供错题回顾",
          r["data"]["details"][0].get("correctOptions") == ["B"])

r = call("POST", "/exam/paper/1/submit", token=member_token,
         body={"1": ["A"], "2": ["A"], "3": ["B"]})
check("重复提交被拒（4002）", r.get("code") == 4002, f"code={r.get('code')}")

# ---------------------------------------------------------------------------
section("步骤 9  排行榜与看板")
r = call("GET", "/ranking/my-org", token=member_token,
         query={"metric": "learn", "periodType": 4, "limit": 10})
check("本组织学时榜查询成功", r.get("code") == 0, str(r.get("message")))
if r.get("code") == 0:
    items = r["data"]
    check("榜单非空（实时写入生效）", len(items) > 0, f"{len(items)} 条")
    if items:
        check("名次从 1 起", items[0].get("rankNo") == 1, str(items[0].get("rankNo")))
        check("本人被标记", any(i.get("self") for i in items))

r = call("GET", "/ranking/my-rank", token=member_token, query={"metric": "learn", "periodType": 4})
check("我的名次查询成功", r.get("code") == 0, str(r.get("message")))

r = call("GET", "/dashboard", token=admin_token)
check("看板查询成功", r.get("code") == 0, str(r.get("message")))
if r.get("code") == 0:
    d = r["data"]
    check("在册人数 > 0", (d.get("rosterCount") or 0) > 0, str(d.get("rosterCount")))
    check("活跃人数 > 0", (d.get("activeUsers") or 0) > 0, str(d.get("activeUsers")))
    check("成绩分布返回 5 个桶", len(d.get("scoreBuckets") or []) == 5,
          str(len(d.get("scoreBuckets") or [])))
    check("平均分 > 0", float(d.get("examAvgScore") or 0) > 0, str(d.get("examAvgScore")))
    check("累计学时 > 0", float(d.get("totalCredit") or 0) > 0, str(d.get("totalCredit")))

r = call("GET", "/roster/page", token=admin_token, query={"pageNo": 1, "pageSize": 20})
check("名册查询成功", r.get("code") == 0, str(r.get("message")))
if r.get("code") == 0:
    check("名册有在册人员", r["data"]["total"] > 0, str(r["data"]["total"]))

r = call("GET", "/roster/ethnicity", token=admin_token)
check("民族统计查询成功", r.get("code") == 0, str(r.get("message")))
if r.get("code") == 0 and r["data"]:
    total_ratio = sum(float(x["ratio"]) for x in r["data"])
    check("各民族占比合计 ≈ 100%", abs(total_ratio - 100.0) < 0.5, f"合计 {total_ratio}%")

# ---------------------------------------------------------------------------
section("步骤 10  鉴权边界")
r = call("GET", "/dashboard", token=None)
check("无 Token 访问被拒（2001）", r.get("code") == 2001, f"code={r.get('code')}")

r = call("GET", "/dashboard", token="invalid-token-xxx")
check("无效 Token 被拒（2001）", r.get("code") == 2001, f"code={r.get('code')}")

print("\n" + "=" * 72)
print(f"冒烟测试完成：通过 {passed} 项，失败 {failed} 项")
print("=" * 72)
sys.exit(1 if failed else 0)
