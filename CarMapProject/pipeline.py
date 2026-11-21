#!/usr/bin/env python3
"""CarMapProject 下脚本共用的配置读取（build-all.py / versions.py）。

数据来源只有两份 JSON，脚本里不再各自解析、也不再硬编码工程名：
  app-builds.json  渠道 + 车型 + 默认渠道（壳工程构建时也是唯一来源）
  projects.json    固定架构栈的发布顺序，以及 artifact → 所属工程

本文件由同目录脚本 `import pipeline` 使用（Python 会把脚本所在目录放进 sys.path）。
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent          # CarMapProject/
BASE = ROOT.parent                              # carProject/，库工程在上一级
CHANNELS_FILE = ROOT / "app-builds.json"
PROJECTS_FILE = ROOT / "projects.json"


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"找不到 {path.name}：{path}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path.name} 不是合法 JSON：{exc}") from exc


def car_models() -> list:
    """车型列表（app-builds.json 的 carModels 键，保持声明顺序）。"""
    models = read_json(CHANNELS_FILE).get("carModels") or {}
    if not models:
        raise SystemExit("app-builds.json 缺少 carModels")
    return list(models)


def default_channel() -> str:
    """默认渠道（app-builds.json 的 defaultChannel）。"""
    channel = read_json(CHANNELS_FILE).get("defaultChannel")
    if not channel:
        raise SystemExit("app-builds.json 缺少 defaultChannel（默认渠道）")
    return channel


def publish_order() -> list:
    """发布顺序 = projects.json 的固定架构栈 + carModels 派生的 HMI 车型工程。

    加车型只需在 app-builds.json 里加 carModels，HMI 工程（Hmi<车型>Project）自动进来；
    只有新增「固定架构层次」的工程才需要动 projects.json。
    """
    entries = read_json(PROJECTS_FILE).get("publishOrder") or []
    if not entries:
        raise SystemExit("projects.json 缺少 publishOrder")
    order = [entry["project"] for entry in entries]
    for model in car_models():
        project = f"Hmi{model.upper()}Project"
        if project not in order:
            order.append(project)
    return order


def artifact_projects() -> dict:
    """artifact（Maven 坐标）→ 所属工程：projects.json + carModels 派生的 hmi-<车型>。"""
    mapping = {}
    for entry in read_json(PROJECTS_FILE).get("publishOrder") or []:
        for artifact in entry.get("artifacts") or []:
            mapping[artifact] = entry["project"]
    for model in car_models():
        mapping[f"hmi-{model}"] = f"Hmi{model.upper()}Project"
    return mapping
