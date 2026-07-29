#!/usr/bin/env python3
"""
Golden Set 自动化评测 runner。

用法：
    python run_golden_set.py [--base http://127.0.0.1:6040] [--cases cases.json] [--only A1,B2]

流程：登录 → 逐条调用 /api/chat（SSE）→ 重组 TOOL_CALL 流式参数 →
判断 load_skill_through_path 实际加载的技能是否与期望一致 → 输出 recall 报告。

cases.json 格式：
    [{"id": "A1", "query": "帮我做一个产品介绍落地页", "expect": ["frontend-style"]},
     {"id": "E1", "query": "你好，你能做什么", "expect": []}]
expect 为空数组表示"不应加载任何技能"；多个元素表示链式（顺序不敏感，包含即命中）。
"""
import argparse
import json
import re
import sys
import urllib.request

def post_json(base, path, body, token=None):
    req = urllib.request.Request(
        base + path,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method="POST")
    return urllib.request.urlopen(req, timeout=900)

def run_case(base, token, case):
    resp = post_json(base, "/api/chat",
                     {"modelConfigId": case.get("modelConfigId", "3"),
                      "message": {"role": "user", "content": case["query"]}},
                     token)
    names, args = {}, {}
    for raw in resp:
        line = raw.decode("utf-8", errors="replace").strip()
        if not line.startswith("data:"):
            continue
        try:
            ev = json.loads(line[5:])
        except json.JSONDecodeError:
            continue
        t = ev.get("type")
        if t == "TOOL_CALL_START":
            names[ev["toolCallId"]] = ev.get("toolCallName")
        elif t == "TOOL_CALL_ARGS":
            args[ev["toolCallId"]] = args.get(ev["toolCallId"], "") + (ev.get("delta") or "")
    loaded = []
    for cid, name in names.items():
        if name == "load_skill_through_path":
            m = re.search(r'"skillId"\s*:\s*"([^"]+)"', args.get(cid, ""))
            if m:
                # 去掉来源后缀（_workspace-writable / _mysql-market / _filesystem-workspace_skills 等）
                loaded.append(re.sub(r"_(workspace|mysql|filesystem)[\w_-]*$", "", m.group(1)))
    return loaded

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:6040")
    ap.add_argument("--cases", default="cases.json")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="admin123")
    ap.add_argument("--only", default=None, help="逗号分隔的 case id，只跑这些")
    args = ap.parse_args()

    login = json.load(post_json(args.base, "/auth/login",
                                {"username": args.user, "password": args.password}))
    token = login["data"]["token"]

    cases = json.load(open(args.cases, encoding="utf-8"))
    if args.only:
        only = set(args.only.split(","))
        cases = [c for c in cases if c["id"] in only]

    hits, misses, false_triggers = 0, [], []
    for case in cases:
        loaded = run_case(args.base, token, case)
        expect = case["expect"]
        if not expect:
            ok = not loaded
            if not ok:
                false_triggers.append((case["id"], loaded))
        else:
            ok = all(e in loaded for e in expect)
            if not ok:
                misses.append((case["id"], expect, loaded))
        hits += ok
        print(f"[{case['id']}] {'PASS' if ok else 'FAIL'} expect={expect} loaded={loaded}")

    print(f"\n=== recall {hits}/{len(cases)} ===")
    if misses:
        print("漏触发/错触发:", misses)
    if false_triggers:
        print("负样本误触发:", false_triggers)
    sys.exit(0 if hits == len(cases) else 1)

if __name__ == "__main__":
    main()
