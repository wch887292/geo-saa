#!/usr/bin/env python3
import json, urllib.request, urllib.parse, urllib.error

BASE = "http://localhost:3000"  # frontend dev server (proxies /api -> backend:8080)

def req(method, path, params=None, token=None, body=None, raw=False):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            code = resp.getcode()
            text = resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        code = e.code
        text = e.read().decode("utf-8", "replace")
    except Exception as e:
        return (-1, str(e)[:120], None)
    try:
        j = json.loads(text)
    except Exception:
        j = None
    return (code, text, j)

def summarize(j):
    if j is None:
        return "(non-json)"
    if isinstance(j, dict):
        code = j.get("code")
        msg = j.get("message")
        data = j.get("data")
        if isinstance(data, dict):
            keys = list(data.keys())[:8]
            return f"code={code} msg={msg} dataKeys={keys}"
        if isinstance(data, list):
            return f"code={code} msg={msg} dataListLen={len(data)}"
        return f"code={code} msg={msg} data={type(data).__name__}"
    return "(json type %s)" % type(j).__name__

# 1) login
lc, lt, lj = req("POST", "/api/v1/auth/login", body={"username":"admin","password":"admin123"})
print("LOGIN            /api/v1/auth/login            -> %s  %s" % (lc, summarize(lj)))
token = None
if lj and isinstance(lj, dict):
    d = lj.get("data") or {}
    token = d.get("token")
print("  token acquired:", bool(token))
print("-"*90)

cases = [
    ("asset",     "GET", "/api/v1/asset/overview", None),
    ("asset",     "GET", "/api/v1/asset/list", {"pageNum":1,"pageSize":10}),
    ("content",   "GET", "/api/v1/content/list", {"pageNum":1,"pageSize":10}),
    ("content",   "GET", "/api/v1/content/templates", None),
    ("dashboard", "GET", "/api/v1/statistics/dashboard", None),
    ("diagnose",  "GET", "/api/v1/diagnose/list", {"pageNum":1,"pageSize":10}),
    ("distribute","GET", "/api/v1/distribute/list", {"pageNum":1,"pageSize":10}),
    ("distribute","GET", "/api/v1/distribute/channels", None),
    ("distribute","GET", "/api/v1/distribute/stats", None),
    ("knowledge", "GET", "/api/v1/knowledge/brands", {"pageNum":1,"pageSize":10}),
    ("monitor",   "GET", "/api/v1/monitor/list", {"pageNum":1,"pageSize":10}),
    ("monitor",   "GET", "/api/v1/monitor/core-metrics", {"brandName":"test"}),
    ("system",    "GET", "/api/v1/system/configs", None),
    ("system",    "GET", "/api/v1/system/model-config", None),
    ("system",    "GET", "/api/v1/system/health", None),  # public
]

ok = 0
fail = 0
for view, method, path, params in cases:
    code, text, j = req(method, path, params=params, token=token)
    summ = summarize(j)
    # treat 2xx with code==200 (backend wraps in {code:200}) as success
    success = (code == 200 and j and isinstance(j, dict) and j.get("code") == 200)
    mark = "OK " if success else "XX "
    if success: ok += 1
    else: fail += 1
    print("%s%-10s %-4s %-38s -> %s  %s" % (mark, view, method, path, code, summ))

print("-"*90)
print("SUMMARY: %d passed, %d failed (out of %d authed GET + login)" % (ok, fail, len(cases)))
