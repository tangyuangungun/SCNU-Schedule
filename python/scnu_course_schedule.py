#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
华南师范大学研究生管理信息系统课表获取脚本。

功能：
1. 自动登录学校统一身份认证；
2. 进入研究生服务平台；
3. 打开“学生课程表”；
4. 获取课程名称、教师、上课时间、地点、周次、学分等信息；
5. 可输出为 JSON 和 CSV。

安装依赖：
    pip install selenium

运行示例：
    python scnu_course_schedule.py
    python scnu_course_schedule.py --show-browser
    python scnu_course_schedule.py --json course_schedule.json --csv course_schedule.csv

安全说明：
    仓库不预置任何真实账号或密码。请通过环境变量 SCNU_ACCOUNT、SCNU_PASSWORD 提供凭据；
    也可以使用命令行参数，但命令行参数可能出现在进程历史中。
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

try:
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support import expected_conditions as EC
    from selenium.webdriver.support.ui import Select, WebDriverWait
except ImportError as exc:  # pragma: no cover
    raise SystemExit("缺少 selenium，请先运行：pip install selenium") from exc


# 不预置任何真实账号或密码。请通过环境变量或命令行参数提供。
DEFAULT_ACCOUNT = os.getenv("SCNU_ACCOUNT", "").strip()
DEFAULT_PASSWORD = os.getenv("SCNU_PASSWORD", "")

PORTAL_URL = "https://gs.scnu.edu.cn/gsapp/sys/yjsemaphome/portal/index.do"
COURSE_TABLE_IFRAME_ID = "iframeContent_wdkbappxskcb"
COURSE_APP_SELECTOR = '.home-app-container[data-app="wdkbapp"][data-menu="xskcb"]'

PERIOD_TIMES: dict[int, tuple[str, str]] = {
    1: ("08:30", "09:10"),
    2: ("09:20", "10:00"),
    3: ("10:20", "11:00"),
    4: ("11:10", "11:50"),
    5: ("14:00", "14:40"),
    6: ("14:50", "15:30"),
    7: ("15:40", "16:20"),
    8: ("16:30", "17:10"),
    9: ("19:00", "19:40"),
    10: ("19:50", "20:30"),
    11: ("20:40", "21:30"),
}

WEEKDAY_ORDER = {
    "星期一": 1,
    "星期二": 2,
    "星期三": 3,
    "星期四": 4,
    "星期五": 5,
    "星期六": 6,
    "星期日": 7,
    "星期天": 7,
}

# 详细课表中的列名到输出字段的映射。
COLUMN_MAP = {
    "课程代码": "course_code",
    "课程名称": "course_name",
    "班级名称": "class_name",
    "课程学时": "hours",
    "学分": "credits",
    "校区": "campus",
    "开课单位": "department",
    "上课方式": "delivery_mode",
    "授课形式": "teaching_mode",
    "任课教师": "teacher",
    "上课人数": "student_count",
    "首次上课日期": "first_class_date",
    "上课时间地点": "schedule_text",
    "选课备注": "selection_note",
    "课表备注": "schedule_note",
}


@dataclass
class SessionInfo:
    semester: str
    student_name: str = ""
    student_id: str = ""
    department: str = ""
    campus: str = ""


def configure_stdout() -> None:
    """尽量使用 UTF-8 输出，避免 Windows 控制台中文乱码。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, OSError):
            pass


def build_driver(show_browser: bool, chrome_binary: str | None) -> webdriver.Chrome:
    options = Options()
    if not show_browser:
        options.add_argument("--headless=new")
    if chrome_binary:
        options.binary_location = chrome_binary

    options.add_argument("--disable-gpu")
    options.add_argument("--no-sandbox")
    options.add_argument("--window-size=1440,1100")
    options.add_argument("--lang=zh-CN")
    options.add_argument("--disable-dev-shm-usage")
    options.add_experimental_option("prefs", {"intl.accept_languages": "zh-CN,zh"})
    return webdriver.Chrome(options=options)


def login_if_needed(
    driver: webdriver.Chrome,
    wait: WebDriverWait,
    account: str,
    password: str,
    show_browser: bool,
) -> None:
    """访问门户，并在跳转到统一认证时自动登录。"""
    print("正在打开研究生服务平台……", file=sys.stderr)
    driver.get(PORTAL_URL)
    wait.until(lambda d: d.current_url != "about:blank")

    # 已登录或浏览器保留了有效会话时，可能直接进入门户。
    if "sso.scnu.edu.cn" not in driver.current_url:
        print("检测到已有有效登录会话。", file=sys.stderr)
        return

    print("正在登录统一身份认证……", file=sys.stderr)
    account_input = wait.until(EC.presence_of_element_located((By.ID, "account")))
    account_input.clear()
    account_input.send_keys(account)

    password_input = driver.find_element(By.ID, "password")
    password_input.clear()
    password_input.send_keys(password)

    # 若学校启用了图形验证码，保留可见浏览器供人工完成，然后继续等待登录跳转。
    captcha_inputs = [
        item
        for item in driver.find_elements(By.ID, "password-random-code")
        if item.is_displayed()
    ]
    if captcha_inputs:
        if not show_browser:
            raise RuntimeError("登录需要图形验证码，请添加 --show-browser 后重试")
        print("检测到登录验证码，请在浏览器中完成验证码并点击登录。", file=sys.stderr)
    else:
        driver.find_element(By.ID, "btn-password-login").click()

    wait.until(EC.url_contains("openapi/auth.html"))
    print("登录成功，正在确认进入研究生系统……", file=sys.stderr)
    confirm = wait.until(
        EC.element_to_be_clickable((By.CSS_SELECTOR, "span.login-check-comfirm"))
    )
    confirm.click()
    wait.until(EC.url_contains("gs.scnu.edu.cn"))


def open_course_table(driver: webdriver.Chrome, wait: WebDriverWait, semester: str | None) -> SessionInfo:
    """进入学生课程表，读取当前学期和学生基本信息。"""
    print("正在打开“学生课程表”……", file=sys.stderr)
    card = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, COURSE_APP_SELECTOR)))
    driver.execute_script("arguments[0].click();", card)

    iframe = wait.until(EC.presence_of_element_located((By.ID, COURSE_TABLE_IFRAME_ID)))
    driver.switch_to.frame(iframe)
    print("正在读取课程信息……", file=sys.stderr)
    wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "table.zero-grid tbody tr")))
    wait.until(
        lambda _d: (
            len(get_table_rows(_d)) > 1
            and "课程名称" in get_table_rows(_d)[0]
        )
    )

    if semester:
        select_element = wait.until(EC.presence_of_element_located((By.ID, "myXnxqSelect")))
        old_text = driver.find_element(By.CSS_SELECTOR, "table.zero-grid").text
        Select(select_element).select_by_value(str(semester))
        wait.until(lambda _d: driver.find_element(By.CSS_SELECTOR, "table.zero-grid").text != old_text)
        wait.until(
            lambda _d: (
                len(get_table_rows(_d)) > 1
                and "课程名称" in get_table_rows(_d)[0]
            )
        )

    semester_select = driver.find_element(By.ID, "myXnxqSelect")
    semester_text = Select(semester_select).first_selected_option.text.strip()
    body_text = driver.find_element(By.TAG_NAME, "body").text

    student_name = student_id = department = campus = ""
    info_match = re.search(
        r"院系：\s*(?P<department>.*?)\s+学号：\s*(?P<student_id>\d+)\s+姓名：\s*(?P<student_name>\S+)",
        body_text,
    )
    if info_match:
        department = info_match.group("department").strip()
        student_id = info_match.group("student_id").strip()
        student_name = info_match.group("student_name").strip()

    if not campus:
        campus_cells = driver.find_elements(By.CSS_SELECTOR, "table.zero-grid tbody tr td:nth-child(6)")
        campus = next((cell.text.strip() for cell in campus_cells if cell.text.strip()), "")

    return SessionInfo(
        semester=semester_text,
        student_name=student_name,
        student_id=student_id,
        department=department,
        campus=campus,
    )


def get_table_rows(driver: webdriver.Chrome) -> list[list[str]]:
    """读取详细课程表格的二维文本数据。"""
    return driver.execute_script(
        """
        const table = document.querySelector('table.zero-grid');
        if (!table) return [];
        return [...table.querySelectorAll('thead tr, tbody tr')]
            .map(tr => [...tr.querySelectorAll('th,td')]
                .map(cell => (cell.innerText || '').trim()));
        """
    )


def period_numbers(period_text: str) -> list[int]:
    numbers: list[int] = []
    for part in period_text.replace("节", "").split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            start, end = part.split("-", 1)
            numbers.extend(range(int(start), int(end) + 1))
        else:
            numbers.append(int(part))
    return numbers


def period_time_range(period_text: str) -> str:
    """将“1-3节”转换成“08:30-11:00”。"""
    ranges: list[str] = []
    for part in period_text.replace("节", "").split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            start_text, end_text = part.split("-", 1)
            start, end = int(start_text), int(end_text)
        else:
            start = end = int(part)
        if start in PERIOD_TIMES and end in PERIOD_TIMES:
            ranges.append(f"{PERIOD_TIMES[start][0]}-{PERIOD_TIMES[end][1]}")
    return "、".join(ranges)


def parse_schedule_text(schedule_text: str) -> list[dict[str, Any]]:
    """
    解析详细表里的“上课时间地点”。

    示例：
        1-16周 星期一[1-2节]★示例教学楼101室
    """
    pattern = re.compile(
        r"(?P<weeks>[^\s]+周)\s*"
        r"(?P<weekday>星期[一二三四五六日天])\s*"
        r"\[(?P<periods>[^\]]+)\]\s*"
        r"(?P<location>.*?)"
        r"(?=\s+[^\s]+周\s*星期[一二三四五六日天]\s*\[|$)"
    )

    meetings: list[dict[str, Any]] = []
    for match in pattern.finditer(schedule_text.replace("\n", " ")):
        periods = match.group("periods").strip()
        numbers = period_numbers(periods)
        location = match.group("location").strip().lstrip("★ ").strip()
        meetings.append(
            {
                "weeks": match.group("weeks").strip(),
                "weekday": match.group("weekday").strip(),
                "periods": periods,
                "period_start": min(numbers) if numbers else None,
                "period_end": max(numbers) if numbers else None,
                "time": period_time_range(periods),
                "location": location,
            }
        )
    return meetings


def parse_courses(raw_rows: list[list[str]]) -> list[dict[str, Any]]:
    if not raw_rows:
        return []

    headers = raw_rows[0]
    courses: list[dict[str, Any]] = []

    for raw_row in raw_rows[1:]:
        if not any(raw_row):
            continue
        values = raw_row + [""] * (len(headers) - len(raw_row))
        mapped = {
            COLUMN_MAP.get(header, header): values[index]
            for index, header in enumerate(headers)
        }
        schedule_text = mapped.pop("schedule_text", "")
        course = {
            "course_code": mapped.get("course_code", ""),
            "course_name": mapped.get("course_name", ""),
            "class_name": mapped.get("class_name", ""),
            "hours": mapped.get("hours", ""),
            "credits": mapped.get("credits", ""),
            "campus": mapped.get("campus", ""),
            "department": mapped.get("department", ""),
            "delivery_mode": mapped.get("delivery_mode", ""),
            "teaching_mode": mapped.get("teaching_mode", ""),
            "teacher": mapped.get("teacher", ""),
            "student_count": mapped.get("student_count", ""),
            "first_class_date": mapped.get("first_class_date", ""),
            "selection_note": mapped.get("selection_note", ""),
            "schedule_note": mapped.get("schedule_note", ""),
            "schedule_text": schedule_text,
            "schedule": parse_schedule_text(schedule_text),
        }
        courses.append(course)

    return courses


def iter_course_meetings(courses: Iterable[dict[str, Any]]) -> Iterable[tuple[dict[str, Any], dict[str, Any]]]:
    for course in courses:
        for meeting in course.get("schedule", []):
            yield course, meeting


def print_schedule(session: SessionInfo, courses: list[dict[str, Any]]) -> None:
    print(f"\n{'=' * 86}")
    print(f"学期：{session.semester}")
    if session.student_name or session.student_id:
        print(f"学生：{session.student_name}  学号：{session.student_id}")
    if session.department:
        print(f"院系：{session.department}")
    print(f"{'=' * 86}\n")

    meetings = list(iter_course_meetings(courses))
    meetings.sort(
        key=lambda pair: (
            WEEKDAY_ORDER.get(pair[1].get("weekday", ""), 99),
            pair[1].get("period_start") or 99,
            pair[0].get("course_name", ""),
        )
    )

    if not meetings:
        print("当前学期未读取到课程安排。")
        return

    for course, meeting in meetings:
        print(
            f"{meeting['weekday']}  {meeting['time']:<13}  "
            f"{course['course_name']}  ({meeting['periods']})"
        )
        print(f"    周次：{meeting['weeks']}")
        print(f"    教师：{course['teacher'] or '未填写'}")
        print(f"    地点：{meeting['location'] or '未填写'}")
        print(f"    学分：{course['credits']}    课程代码：{course['course_code']}")
        print()


def write_json(path: Path, session: SessionInfo, courses: list[dict[str, Any]]) -> None:
    payload = {
        "fetched_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "semester": session.semester,
        "student": {
            "name": session.student_name,
            "student_id": session.student_id,
            "department": session.department,
        },
        "courses": courses,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def write_csv(path: Path, courses: list[dict[str, Any]]) -> None:
    fieldnames = [
        "course_code",
        "course_name",
        "class_name",
        "credits",
        "hours",
        "campus",
        "department",
        "teacher",
        "first_class_date",
        "weeks",
        "weekday",
        "periods",
        "time",
        "location",
        "schedule_text",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for course, meeting in iter_course_meetings(courses):
            writer.writerow(
                {
                    "course_code": course.get("course_code", ""),
                    "course_name": course.get("course_name", ""),
                    "class_name": course.get("class_name", ""),
                    "credits": course.get("credits", ""),
                    "hours": course.get("hours", ""),
                    "campus": course.get("campus", ""),
                    "department": course.get("department", ""),
                    "teacher": course.get("teacher", ""),
                    "first_class_date": course.get("first_class_date", ""),
                    "weeks": meeting.get("weeks", ""),
                    "weekday": meeting.get("weekday", ""),
                    "periods": meeting.get("periods", ""),
                    "time": meeting.get("time", ""),
                    "location": meeting.get("location", ""),
                    "schedule_text": course.get("schedule_text", ""),
                }
            )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="自动登录华南师大研究生系统并获取学生课程表。"
    )
    parser.add_argument("--account", default=DEFAULT_ACCOUNT, help="登录账号；建议使用 SCNU_ACCOUNT 环境变量")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="登录密码；建议使用 SCNU_PASSWORD 环境变量")
    parser.add_argument("--semester", help="学期值，例如 20261；不填则使用网页当前选择的学期")
    parser.add_argument("--show-browser", action="store_true", help="显示 Chrome 窗口；遇到验证码时建议使用")
    parser.add_argument("--chrome-binary", help="Chrome 可执行文件路径；通常无需指定")
    parser.add_argument("--timeout", type=int, default=40, help="等待页面元素或跳转的秒数，默认 40")
    parser.add_argument("--json", dest="json_path", type=Path, help="将完整结果写入 JSON 文件")
    parser.add_argument("--csv", dest="csv_path", type=Path, help="将课程安排写入 CSV 文件")
    args = parser.parse_args()
    if not args.account or not args.password:
        parser.error("缺少登录凭据；请设置 SCNU_ACCOUNT、SCNU_PASSWORD 或传入 --account、--password。")
    return args


def main() -> int:
    configure_stdout()
    args = parse_args()

    driver: webdriver.Chrome | None = None
    try:
        driver = build_driver(args.show_browser, args.chrome_binary)
        wait = WebDriverWait(driver, args.timeout)
        login_if_needed(driver, wait, args.account, args.password, args.show_browser)
        session = open_course_table(driver, wait, args.semester)
        courses = parse_courses(get_table_rows(driver))

        print_schedule(session, courses)

        if args.json_path:
            write_json(args.json_path, session, courses)
            print(f"JSON 已保存：{args.json_path.resolve()}")
        if args.csv_path:
            write_csv(args.csv_path, courses)
            print(f"CSV 已保存：{args.csv_path.resolve()}")

        return 0
    except Exception as exc:
        print(f"\n获取课表失败：{type(exc).__name__}: {exc}", file=sys.stderr)
        if not args.show_browser:
            print("可改用 --show-browser 查看页面状态或手动完成验证码。", file=sys.stderr)
        return 1
    finally:
        if driver is not None:
            driver.quit()


if __name__ == "__main__":
    raise SystemExit(main())
