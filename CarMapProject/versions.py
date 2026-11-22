#!/usr/bin/env python3
"""AAR 版本管理：改 CarMapProject/versions.properties（全仓库唯一版本来源）。

各工程构建时**直接读这个文件**（见各 build.gradle.kts 的 `skyVersionOf`），
所以改完不需要同步到任何地方；唯一要做的是重新 publish 受影响的工程，
`maven-repo` 里才会有新版本的 AAR。

用法（在 CarMapProject 目录下）：
  ./versions.py list                               # 查看各 artifact 当前版本
  ./versions.py bump map-sdk-amap-850 1.0.1        # 只升这一个
  ./versions.py bump map-api=1.0.2 business=1.0.3  # 一次升多个
  ./versions.py bump --all 1.1.0                   # 全部升到同一版本

只管 AAR 版本（Maven 坐标），不动 APK 的 versionName / versionCode ——
后者在 CarMapProject/build.gradle.kts 的 skyBuild.* 里。
"""
import re
import sys

import pipeline

ROOT = pipeline.ROOT                        # CarMapProject/
SRC = ROOT / "versions.properties"

VERSION_RE = re.compile(r"^\d+(\.\d+)+([-.][0-9A-Za-z]+)*$")
ENTRY_RE = re.compile(r"^([A-Za-z0-9._-]+)=(.*)$")


def read_versions() -> dict:
    """按行读版本表，保持文件里的顺序（跳过注释与空行）。"""
    table = {}
    for raw in SRC.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"{SRC} 行格式错误（应为 artifact=版本）：{raw!r}")
        key, value = line.split("=", 1)
        table[key.strip()] = value.strip()
    if not table:
        raise SystemExit(f"版本表是空的：{SRC}")
    return table


def set_versions(new_versions: dict) -> set:
    """按 artifact → 新版本 改写版本表（保留注释与原有顺序），返回改动的 artifact。"""
    known = read_versions()
    unknown = [a for a in new_versions if a not in known]
    if unknown:
        raise SystemExit(
            f"✗ 版本表里没有 artifact：{', '.join(unknown)}（versions.py list 可查看全部名称）"
        )
    invalid = [v for v in new_versions.values() if not VERSION_RE.match(v)]
    if invalid:
        raise SystemExit(f"✗ 版本号格式不对：{', '.join(invalid)}（应形如 1.0.1 / 1.2.0-beta1）")

    lines = []
    for line in SRC.read_text(encoding="utf-8").splitlines():
        match = ENTRY_RE.match(line)
        if match and match.group(1) in new_versions:
            artifact = match.group(1)
            lines.append(f"{artifact}={new_versions[artifact]}")
            print(f"  {artifact:<22} {known[artifact]} → {new_versions[artifact]}")
        else:
            lines.append(line)
    SRC.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return set(new_versions)


def publish_hint(artifact: str) -> str:
    project = pipeline.artifact_projects().get(artifact)
    if not project:
        return "未识别所属工程，请手工 publish（可在 projects.json 补 artifacts）"
    return f"(cd ../{project} && ./gradlew publish)"


def usage() -> int:
    print("用法：")
    print("  ./versions.py list")
    print("  ./versions.py bump <artifact> <版本>        例如 bump map-sdk-amap-850 1.0.1")
    print("  ./versions.py bump <artifact>=<版本> ...    例如 bump map-api=1.0.2 business=1.0.3")
    print("  ./versions.py bump --all <版本>")
    return 1


def bump(args: list) -> int:
    table = read_versions()

    if not args:
        return usage()
    if args[0] == "--all":
        if len(args) != 2:
            return usage()
        print("全部升级：")
        changes = dict.fromkeys(table, args[1])
    elif len(args) == 2 and "=" not in args[0]:
        print("单模块升级：")
        changes = {args[0]: args[1]}
    else:
        print("按表升级：")
        changes = {}
        for pair in args:
            if "=" not in pair:
                print(f"✗ 参数无法解析：{pair}", file=sys.stderr)
                return usage()
            artifact, version = pair.split("=", 1)
            changes[artifact] = version

    affected = set_versions(changes)

    print()
    print("版本表已改；各工程下次构建即按新版本解析，只需重发受影响的 AAR：")
    for artifact in sorted(affected):
        print(f"  · {artifact}: {publish_hint(artifact)}")

    print()
    print("然后重新打包：")
    print("  ./gradlew :app:assembleDefault        # 默认渠道；全部渠道用 assembleDebug")
    print("全量重发（不打 APK）：./CarMapProject/build-all.py；要 APK 加 --assemble")
    return 0


def main() -> int:
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        return usage()

    command = args[0]
    if command in ("list", "--list"):
        for artifact, version in read_versions().items():
            print(f"{artifact} = {version}")
        return 0
    if command == "bump":
        return bump(args[1:])

    print(f"✗ 未知子命令：{command}", file=sys.stderr)
    return usage()


if __name__ == "__main__":
    sys.exit(main())
