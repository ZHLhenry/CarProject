#!/usr/bin/env python3
"""一键构建：按架构依赖顺序发布各独立工程，再按「渠道」组装壳工程。

    基建层 → 地图适配层 → 业务组件层 → HMI 契约 → HMI 车型工程 → 壳工程

用法（在 CarMapProject 目录下，或在 carProject 根执行 ./CarMapProject/build-all.py）：

  ./build-all.py                      # 发布全部 AAR，再打「默认渠道」Debug 包
  ./build-all.py --release            # 发布全部 AAR，再打「默认渠道」Release 包
  ./build-all.py --all-channels       # 发布全部 AAR，再打全部渠道的 Debug 包
  ./build-all.py --all-channels --release
  ./build-all.py assembleE245_h6_8295_front_1920x720_amap850Debug
                                      # 透传任意 gradle 任务（可给多个）

渠道不是车型：一个渠道一个 APK。默认只打 app-builds.json 里 defaultChannel
那一个（= :app:assembleDefault），要全量才加 --all-channels。换默认渠道只改 JSON。

发布顺序、车型列表、默认渠道都从 app-builds.json / projects.json 读（见 pipeline.py），
本脚本不硬编码任何工程名或渠道名。
"""
import os
import subprocess
import sys
from pathlib import Path

import pipeline

ROOT = pipeline.ROOT
BASE = pipeline.BASE

# 默认渠道的别名任务由 app/build.gradle.kts 注册（渠道 ID → 变体名的推导只在那里做）
DEFAULT_TASKS = {
    "Debug": ":app:assembleDefault",
    "Release": ":app:assembleDefaultRelease",
}


def gradlew(directory: Path) -> str:
    return str(directory / ("gradlew.bat" if os.name == "nt" else "gradlew"))


def run(title: str, cmd: list, cwd: Path) -> None:
    print(flush=True)
    print(f"=========== {title} ===========", flush=True)
    print(f"$ {' '.join(cmd)}", flush=True)
    subprocess.run(cmd, cwd=str(cwd), check=True)


def list_outputs() -> None:
    print()
    print("APK 产物：")
    apks = sorted((ROOT / "app/build/outputs/renamed-apk").glob("*/*/*.apk"))
    for apk in apks:
        print(f"  {apk.relative_to(BASE)}")
    if not apks:
        print("  （无）")

    print()
    print("AAR 产物（maven-repo，壳工程编译时从这里取）：")
    aars = sorted((ROOT / "maven-repo").rglob("*.aar"))
    for aar in aars:
        print(f"  {aar.relative_to(BASE)}")
    if not aars:
        print("  （无）")


def main() -> int:
    args = sys.argv[1:]
    all_channels = "--all-channels" in args or "-a" in args
    release = "--release" in args
    extra = [a for a in args if a not in ("--all-channels", "-a", "--release")]

    build_type = "Release" if release else "Debug"
    if extra:
        tasks, scope = extra, "透传任务"
    elif all_channels:
        # assembleDebug / assembleRelease = 该 buildType 下全部渠道
        tasks, scope = [f":app:assemble{build_type}"], f"全部渠道 {build_type}"
    else:
        tasks, scope = [DEFAULT_TASKS[build_type]], f"默认渠道 {pipeline.default_channel()} {build_type}"

    print(f"壳工程打包范围：{scope}")
    print(f"  → {' '.join(tasks)}")
    print(f"发布顺序：{' → '.join(pipeline.publish_order())}")

    try:
        # 各工程读自己 gradle.properties 里的 automap.version.*，构建前先与版本表对齐
        run("sync versions", [sys.executable, str(ROOT / "sync-versions.py")], ROOT)

        # 地图 SDK 继承链静态校验（CI 门禁，publish 之前必须先绿）
        run(
            "verify: SkyMapAdapter SDK 继承链",
            [gradlew(BASE / "SkyMapAdapter"), "verifyMapSdkChain", "--console=plain"],
            BASE / "SkyMapAdapter",
        )

        for project in pipeline.publish_order():
            target = BASE / project
            if not target.is_dir():
                print(flush=True)
                print(f"⚠ 跳过 {project}：目录不存在 {target}", flush=True)
                continue
            run(f"publish: {project}", [gradlew(target), "publish", "--console=plain"], target)

        run("assemble: CarMapProject", [gradlew(ROOT), *tasks, "--console=plain"], ROOT)
    except subprocess.CalledProcessError as exc:
        print()
        print(f"✗ 失败（退出码 {exc.returncode}）：{' '.join(exc.cmd)}")
        return exc.returncode or 1

    list_outputs()
    return 0


if __name__ == "__main__":
    sys.exit(main())
