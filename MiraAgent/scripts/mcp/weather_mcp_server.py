#!/usr/bin/env python3
"""A dependency-free MCP server exposing a weather lookup tool, over JSON-RPC 2.0 / stdio.

Implements the subset MiraAgent uses: initialize, notifications/initialized,
tools/list, tools/call. Exposes one tool: get_weather(city) -> 当前天气摘要。

数据源：Open-Meteo（https://open-meteo.com）——免费、免 API key。
先用其 geocoding API 把城市名解析成经纬度，再取当前天气。仅用标准库（urllib）。
启用方式：把 mira.mcp 指向 `python3 weather_mcp_server.py`（见 application.yaml 注释示例）。
"""
import json
import sys
import urllib.parse
import urllib.request

PROTOCOL_VERSION = "2024-11-05"
TIMEOUT_SECONDS = 10

# WMO weather code -> 中文描述（Open-Meteo current.weather_code）
WEATHER_CODES = {
    0: "晴", 1: "大致晴朗", 2: "局部多云", 3: "阴",
    45: "雾", 48: "雾凇",
    51: "小毛毛雨", 53: "毛毛雨", 55: "大毛毛雨",
    56: "冻毛毛雨", 57: "强冻毛毛雨",
    61: "小雨", 63: "中雨", 65: "大雨",
    66: "冻雨", 67: "强冻雨",
    71: "小雪", 73: "中雪", 75: "大雪", 77: "雪粒",
    80: "小阵雨", 81: "阵雨", 82: "强阵雨",
    85: "小阵雪", 86: "阵雪",
    95: "雷暴", 96: "雷暴伴小冰雹", 99: "雷暴伴大冰雹",
}

TOOLS = [
    {
        "name": "get_weather",
        "description": "查询某个城市的当前天气（温度、天气状况、体感温度、湿度、风速）。输入城市名（中英文均可）。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "城市名称，如「北京」「上海」「Tokyo」"}
            },
            "required": ["city"],
        },
    }
]


def _get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "MiraAgent-weather-mcp/0.1"})
    with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _geocode(city):
    """城市名 -> (显示名, 纬度, 经度)，失败返回 None。"""
    q = urllib.parse.urlencode({"name": city, "count": 1, "language": "zh", "format": "json"})
    data = _get_json("https://geocoding-api.open-meteo.com/v1/search?" + q)
    results = data.get("results") or []
    if not results:
        return None
    r = results[0]
    label_parts = [p for p in (r.get("name"), r.get("admin1"), r.get("country")) if p]
    return (", ".join(label_parts), r.get("latitude"), r.get("longitude"))


def get_weather(city):
    if not city or not str(city).strip():
        return "请提供城市名称。"
    geo = _geocode(str(city).strip())
    if geo is None:
        return f"没有找到城市「{city}」，请确认名称是否正确。"
    label, lat, lon = geo
    q = urllib.parse.urlencode({
        "latitude": lat, "longitude": lon,
        "current": "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m",
        "timezone": "auto",
    })
    data = _get_json("https://api.open-meteo.com/v1/forecast?" + q)
    cur = data.get("current") or {}
    desc = WEATHER_CODES.get(cur.get("weather_code"), "未知")
    return (
        f"{label} 当前天气：{desc}，气温 {cur.get('temperature_2m')}°C"
        f"（体感 {cur.get('apparent_temperature')}°C），"
        f"湿度 {cur.get('relative_humidity_2m')}%，风速 {cur.get('wind_speed_10m')} km/h。"
    )


def call_tool(name, args):
    if name == "get_weather":
        try:
            text = get_weather((args or {}).get("city"))
            return {"content": [{"type": "text", "text": text}]}
        except Exception as e:  # noqa: BLE001 — 网络/解析异常转为工具错误，不崩溃
            return {"content": [{"type": "text", "text": f"查询天气失败：{e}"}], "isError": True}
    return {"content": [{"type": "text", "text": "unknown tool: " + str(name)}], "isError": True}


def handle(req):
    method = req.get("method")
    if method == "initialize":
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "weather-mcp-server", "version": "0.1.0"},
        }
    if method == "tools/list":
        return {"tools": TOOLS}
    if method == "tools/call":
        params = req.get("params", {})
        return call_tool(params.get("name"), params.get("arguments", {}))
    raise ValueError("unknown method: " + str(method))


def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except json.JSONDecodeError:
            continue
        if "id" not in req:  # 通知无需响应
            continue
        resp = {"jsonrpc": "2.0", "id": req["id"]}
        try:
            resp["result"] = handle(req)
        except Exception as e:  # noqa: BLE001
            resp["error"] = {"code": -32603, "message": str(e)}
        sys.stdout.write(json.dumps(resp) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
