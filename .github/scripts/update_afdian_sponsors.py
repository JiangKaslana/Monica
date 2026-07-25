#!/usr/bin/env python3
"""Update the Afdian sponsor block in README.md."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
try:
    from zoneinfo import ZoneInfo
except ImportError:  # pragma: no cover - Python 3.8 fallback
    ZoneInfo = None

API_URL = "https://afdian.com/api/open"
START = "<!-- afdian-sponsors:start -->"
END = "<!-- afdian-sponsors:end -->"
USER_ID = "8b131882c4ec11f0b26e52540025c377"


def shanghai_now() -> datetime:
    if ZoneInfo is not None:
        try:
            return datetime.now(ZoneInfo("Asia/Shanghai"))
        except Exception:
            pass
    return datetime.now(timezone(timedelta(hours=8)))


def shanghai_from_timestamp(timestamp: int) -> datetime:
    if ZoneInfo is not None:
        try:
            return datetime.fromtimestamp(timestamp, ZoneInfo("Asia/Shanghai"))
        except Exception:
            pass
    return datetime.fromtimestamp(timestamp, timezone(timedelta(hours=8)))


def api_call(token: str, user_id: str, endpoint: str, page: int, per_page: int = 100) -> dict:
    params = json.dumps({"page": page, "per_page": per_page}, ensure_ascii=False, separators=(",", ":"))
    fields = {"params": params, "ts": str(int(datetime.now(timezone.utc).timestamp())), "user_id": user_id}
    canonical = "".join(key + fields[key] for key in sorted(fields))
    fields["sign"] = hashlib.md5((token + canonical).encode("utf-8")).hexdigest()
    request = urllib.request.Request(
        f"{API_URL}/{endpoint}",
        data=urllib.parse.urlencode(fields).encode("utf-8"),
        headers={"User-Agent": "Monica-Afdian-README-Updater/1.0"},
    )
    payload = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                payload = json.load(response)
            break
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)
    if payload.get("ec") != 200:
        raise RuntimeError(f"爱发电接口 {endpoint} 返回错误: {payload.get('em', 'unknown')} ({payload.get('ec')})")
    return payload["data"]


def paged(token: str, user_id: str, endpoint: str) -> list[dict]:
    items: list[dict] = []
    page = 1
    while True:
        data = api_call(token, user_id, endpoint, page)
        batch = data.get("list") or []
        items.extend(batch)
        if page >= int(data.get("total_page") or page) or not batch:
            return items
        page += 1


def amount(value: object) -> Decimal:
    try:
        return Decimal(str(value or "0"))
    except InvalidOperation:
        return Decimal("0")


def display_name(value: object) -> str:
    name = str(value or "爱发电支持者")
    if len(name) == 11 and name.isdigit():
        return f"{name[:3]}****{name[7:]}"
    return name


def current_month_total(orders: list[dict]) -> Decimal:
    now = shanghai_now()
    total = Decimal("0")
    for order in orders:
        if int(order.get("status") or 0) != 2:
            continue
        created = shanghai_from_timestamp(int(order.get("create_time") or 0))
        if created.year == now.year and created.month == now.month:
            total += amount(order.get("show_amount", order.get("total_amount")))
    return total


def render(sponsors: list[dict], month_total: Decimal) -> str:
    visible = sorted(sponsors, key=lambda item: amount(item.get("all_sum_amount")), reverse=True)
    cards = []
    for sponsor in visible:
        user = sponsor.get("user") or {}
        name = html.escape(display_name(user.get("name")), quote=True)
        avatar = html.escape(str(user.get("avatar") or "https://pic1.afdiancdn.com/default/avatar/avatar-purple.png?imageView2/1/"), quote=True)
        total = amount(sponsor.get("all_sum_amount"))
        cards.append(f'<td align="center" width="120"><a href="https://afdian.com/a/JoyinJoester" title="{name}"><img src="{avatar}" alt="{name}" width="56" height="56" /></a><br /><sub>{name}<br />¥{total:.2f}</sub></td>')
    if cards:
        rows = ["<tr>" + "".join(cards[index:index + 6]) + "</tr>" for index in range(0, len(cards), 6)]
        body = "<table>\n" + "\n".join(rows) + "\n</table>"
    else:
        body = "<div align=\"center\"><sub>名单将在收到首笔公开打赏后自动更新。</sub></div>"
    return f"{START}\n### 爱发电鸣谢\n\n感谢每一位支持 Monica 的朋友！\n\n**本月打赏金额：¥{month_total:.2f}**\n\n{body}\n{END}"


def update_readme(path: Path, block: str) -> None:
    text = path.read_text(encoding="utf-8")
    start = text.find(START)
    end = text.find(END)
    if start < 0 or end < start:
        raise RuntimeError(f"README 缺少生成标记: {START} / {END}")
    end += len(END)
    path.write_text(text[:start] + block + text[end:], encoding="utf-8")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    parser.add_argument("--token", default=os.environ.get("AFDIAN_TOKEN"))
    parser.add_argument("--user-id", default=os.environ.get("AFDIAN_USER_ID", USER_ID))
    args = parser.parse_args()
    if not args.token:
        raise RuntimeError("缺少 AFDIAN_TOKEN；请通过环境变量或 GitHub Secret 提供")
    sponsors = paged(args.token, args.user_id, "query-sponsor")
    orders = paged(args.token, args.user_id, "query-order")
    update_readme(args.readme, render(sponsors, current_month_total(orders)))
    print(f"已更新 {args.readme}：{len(sponsors)} 位支持者，本月 ¥{current_month_total(orders):.2f}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"错误: {error}", file=sys.stderr)
        raise SystemExit(1)
