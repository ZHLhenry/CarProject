#!/usr/bin/env python3
"""升级 AAR 版本：改的是 CarMapProject/versions.properties（全仓库唯一版本来源）。

用法：
  ./bump-version.py --list                        查看当前各 artifact 的版本
  ./bump-version.py map-sdk-amap-850 1.0.1        只升这一个，其余保持不动
  ./bump-version.py map-api=1.0.2 business=1.0.3  一次升多个
  ./bump-version.py --all 1.1.0                   全部升到同一版本

升完只需 publish 受影响的工程（脚本末尾会打印具体命令），不必重发全部 AAR；
壳工程下次构建即按新版本解析。
注意：本脚本只管 AAR 版本（Maven 坐标），不动 APK 的 versionName / versionCode
      ——那套在 CarMapProject/build.gradle.kts 的 skyBuild.* 里。
"""
import re
import subprocess
import sys
from pathlib import Path

import pipeline

ROOT = pipeline.ROOT
VERSIONS_FILE = ROOT / "versions.properties"

VERSION_RE = re.compile(r"^\d+(\.\d+)+([-.][0-9A-Za-z]+)*$")
ENTRY_RE = re.compile(r"^([A-Za-z0-9._-]+)=(.*)$")


def usage() -> int:
    print("用法：")
    print("  ./bump-version.py --list")
    print("  ./bump-version.py <artifact> <版本>         例如 map-sdk-amap-850 1.0.1")
    print("  ./bump-version.py <artifact>=<版本> ...     例如 map-api=1.0.2 business=1.0.3")
    print("  ./bump-version.py --all <版本>")
    return 1


def publish_hint(artifact: str) -> str:
    project = pipeline.artifact_projects().get(artifact)
    if not project:
        return "未识别所属工程，请手工 publish（可在 projects.json 补 artifacts）"
    return f"(cd ../{project} && ./gradlew publish)"


def load_entries() -> list:
    return [ENTRY_RE.match(line) for line in VERSIONS_FILE.read_text(encoding="utf-8").splitlines()]


def table() -> dict:
    return {m.group(1): m.group(2) for m in load_entries() if m}


def set_versions(new_versions: dict) -> list:
    """按 artifact → 新版本 改写版本表，返回实际改动的 artifact 列表。"""
    unknown = [a for a in new_versions if a not in table()]
    if unknown:
        raise SystemExit(
            f"✗ 版本表里没有 artifact：{', '.join(unknown)}（--list 可查看全部名称）"
        )
    invalid = [v for v in new_versions.values() if not VERSION_RE.match(v)]
    if invalid:
        raise SystemExit(
            f"✗ 版本号格式不对：{', '.join(invalid)}（应形如 1.0.1 / 1.2.0-beta1）"
        )

    old = table()
    lines = []
    for line in VERSIONS_FILE.read_text(encoding="utf-8").splitlines():
        m = ENTRY_RE.match(line)
        if m and m.group(1) in new_versions:
            artifact = m.group(1)
            lines.append(f"{artifact}={new_versions[artifact]}")
            print(f"  {artifact:<22} {old[artifact]} → {new_versions[artifact]}")
        else:
            lines.append(line)
    VERSIONS_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return list(new_versions)


def main() -> int:
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        return usage()

    if args[0] == "--list":
        for artifact, version in table().items():
            print(f"{artifact} = {version}")
        return 0

    changes = {}
    if args[0] == "--all":
        if len(args) != 2:
            return usage()
        print("全部升级：")
        changes = {a: args[1] for a in table()}
    elif len(args) == 2 and "=" not in args[0]:
        print("单模块升级：")
        changes = {args[0]: args[1]}
    else:
        print("按表升级：")
        for pair in args:
            if "=" not in pair:
                print(f"✗ 参数无法解析：{pair}", file=sys.stderr)
                return usage()
            artifact, version = pair.split("=", 1)
            changes[artifact] = version

    affected = set_versions(changes)

    # 版本表是唯一来源，但各工程实际读的是自己 gradle.properties 里的 automap.version.*，
    # 改完必须同步，否则构建仍按旧版本解析。
    print(flush=True)
    result = subprocess.run([sys.executable, str(ROOT / "sync-versions.py")], cwd=str(ROOT))
    if result.returncode != 0:
        print("✗ 同步失败：请手工执行 ./sync-versions.py", file=sys.stderr)
        return result.returncode

    print()
    print("只需发布受影响的工程（其余 AAR 不必重发）：")
    for artifact in sorted(affected):
        print(f"  · {artifact}: {publish_hint(artifact)}")

    print()
    print("然后重新打包（壳工程按新版本解析）：")
    print("  ./gradlew :app:assembleDefault   # 默认渠道；全部渠道用 --all-channels")
    print("全量重发 + 打包仍是：./CarMapProject/build-all.py（在 carProject 根执行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
