#!/usr/bin/env python3
"""按架构依赖顺序发布各独立工程，可选再打壳工程 APK。

    基建层 → 地图适配层 → 业务组件层 → HMI 契约 → HMI 车型工程

发布 AAR 与打 APK 是两件事，所以**默认只发布**；要 APK 才显式加参数
（日常改完代码通常只需要其中一个，混在一起会白跑 6 次 Gradle + 一次继承链校验）。

用法（在 CarMapProject 目录下，或在 carProject 根执行 ./CarMapProject/build-all.py）：

  ./build-all.py                      # 只发布全部 AAR（sync versions + 继承链校验 + publish）
  ./build-all.py --assemble           # 发布后打「默认渠道」Debug 包
  ./build-all.py --assemble --release # 发布后打「默认渠道」Release 包
  ./build-all.py --all-channels       # 发布后打全部渠道的 Debug 包
  ./build-all.py assembleE245_h6_8295_front_1920x720_amap850Debug
                                      # 发布后透传任意 gradle 任务（可给多个）

渠道不是车型：一个渠道一个 APK。默认渠道取 app-builds.json 的 defaultChannel
（= :app:assembleDefault）；换默认渠道只改 JSON。

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


def list_outputs(show_apk: bool) -> None:
    print()
    print("AAR 产物（maven-repo，壳工程编译时从这里取）：")
    aars = sorted((ROOT / "maven-repo").rglob("*.aar"))
    for aar in aars:
        print(f"  {aar.relative_to(BASE)}")
    if not aars:
        print("  （无）")

    if not show_apk:
        return

    print()
    print("APK 产物：")
    apks = sorted((ROOT / "app/build/outputs/renamed-apk").glob("*/*/*.apk"))
    for apk in apks:
        print(f"  {apk.relative_to(BASE)}")
    if not apks:
        print("  （无）")


def main() -> int:
    args = sys.argv[1:]
    want_assemble = "--assemble" in args
    all_channels = "--all-channels" in args
    release = "--release" in args
    extra = [a for a in args if a not in ("--assemble", "--all-channels", "--release")]

    build_type = "Release" if release else "Debug"
    if extra:
        tasks, scope = extra, "透传任务"
    elif all_channels:
        # assembleDebug / assembleRelease = 该 buildType 下全部渠道
        tasks, scope = [f":app:assemble{build_type}"], f"发布 AAR + 全部渠道 {build_type}"
    elif want_assemble:
        tasks, scope = [DEFAULT_TASKS[build_type]], (
            f"发布 AAR + 默认渠道 {pipeline.default_channel()} {build_type}"
        )
    else:
        tasks, scope = [], "只发布 AAR（不打 APK）"

    if release and not tasks:
        print("⚠ --release 需要配合 --assemble / --all-channels / 显式 gradle 任务才有意义，已忽略")

    print(f"本次范围：{scope}")
    if tasks:
        print(f"  → {' '.join(tasks)}")
    print(f"发布顺序：{' → '.join(pipeline.publish_order())}")

    try:
        # 各工程读自己 gradle.properties 里的 automap.version.*，构建前先与版本表对齐
        run("sync versions", [sys.executable, str(ROOT / "versions.py"), "sync"], ROOT)

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

        if tasks:
            run("assemble: CarMapProject", [gradlew(ROOT), *tasks, "--console=plain"], ROOT)
        else:
            print()
            print("已发布 AAR；需要 APK 时再单独打包：")
            print("  ./gradlew :app:assembleDefault     # 默认渠道 Debug")
            print("  ./gradlew assembleDebug            # 全部渠道 Debug")
            print("  ./build-all.py --assemble          # 发布 + 默认渠道 Debug（一条命令）")
    except subprocess.CalledProcessError as exc:
        print()
        print(f"✗ 失败（退出码 {exc.returncode}）：{' '.join(exc.cmd)}")
        return exc.returncode or 1

    list_outputs(show_apk=bool(tasks))
    return 0


if __name__ == "__main__":
    sys.exit(main())
