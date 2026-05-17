"""
One-off: remove demo (keep upload), flatten com.example.mgdemoplus.*.dp / dp.* / utils.dp.
Run from repo root: python scripts/refactor_flatten_packages.py
"""
from __future__ import annotations

import os
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "src" / "main" / "java" / "com" / "example" / "mgdemoplus"
TEST = ROOT / "src" / "test" / "java" / "com" / "example" / "mgdemoplus"

TEXT_GLOBS = ("*.java", "*.yml", "*.md", "*.properties", "*.kotlin")  # yaml limited

REPLACEMENTS_CONTENT: list[tuple[str, str]] = [
    ("com.example.mgdemoplus.service.serviceImpl.dp.npc.", "com.example.mgdemoplus.service.serviceImpl.npc."),
    ("com.example.mgdemoplus.dp.quickmatch.pairing.", "com.example.mgdemoplus.quickmatch.pairing."),
    ("com.example.mgdemoplus.dp.quickmatch.", "com.example.mgdemoplus.quickmatch."),
    ("com.example.mgdemoplus.dp.presence.", "com.example.mgdemoplus.presence."),
    ("com.example.mgdemoplus.mapper.demo.", "com.example.mgdemoplus.mapper."),
    ("com.example.mgdemoplus.service.demo.", "com.example.mgdemoplus.service."),
    ("com.example.mgdemoplus.mapper.dp.", "com.example.mgdemoplus.mapper."),
    ("com.example.mgdemoplus.entity.dp.", "com.example.mgdemoplus.entity."),
    ("com.example.mgdemoplus.controller.dp.", "com.example.mgdemoplus.controller."),
    ("com.example.mgdemoplus.service.dp.", "com.example.mgdemoplus.service."),
    ("com.example.mgdemoplus.service.serviceImpl.dp.", "com.example.mgdemoplus.service.serviceImpl."),
    ("com.example.mgdemoplus.utils.dp.", "com.example.mgdemoplus.utils."),
    ("package com.example.mgdemoplus.mapper.demo;", "package com.example.mgdemoplus.mapper;"),
    ("package com.example.mgdemoplus.service.serviceImpl.demo;", "package com.example.mgdemoplus.service.serviceImpl;"),
    ("package com.example.mgdemoplus.service.demo;", "package com.example.mgdemoplus.service;"),
    ("package com.example.mgdemoplus.controller.demo;", "package com.example.mgdemoplus.controller;"),
    ("package com.example.mgdemoplus.mapper.dp;", "package com.example.mgdemoplus.mapper;"),
    ("package com.example.mgdemoplus.entity.dp;", "package com.example.mgdemoplus.entity;"),
    ("package com.example.mgdemoplus.controller.dp;", "package com.example.mgdemoplus.controller;"),
    ("package com.example.mgdemoplus.service.dp;", "package com.example.mgdemoplus.service;"),
    ("package com.example.mgdemoplus.service.serviceImpl.dp.npc;", "package com.example.mgdemoplus.service.serviceImpl.npc;"),
    ("package com.example.mgdemoplus.service.serviceImpl.dp;", "package com.example.mgdemoplus.service.serviceImpl;"),
    ("package com.example.mgdemoplus.dp.quickmatch.pairing;", "package com.example.mgdemoplus.quickmatch.pairing;"),
    ("package com.example.mgdemoplus.dp.quickmatch;", "package com.example.mgdemoplus.quickmatch;"),
    ("package com.example.mgdemoplus.dp.presence;", "package com.example.mgdemoplus.presence;"),
    ("package com.example.mgdemoplus.utils.dp;", "package com.example.mgdemoplus.utils;"),
]

DELETE_REL = [
    "controller/demo/StudentController.java",
    "controller/demo/MovieController.java",
    "controller/demo/RedisLabController.java",
    "service/demo/StudentService.java",
    "service/demo/MovieService.java",
    "service/demo/RedisLabService.java",
    "service/serviceImpl/demo/StudentServiceImpl.java",
    "service/serviceImpl/demo/MovieServiceImpl.java",
    "mapper/demo/StudentMapper.java",
    "mapper/demo/MovieMapper.java",
    "entity/demo/Student.java",
    "entity/demo/Movie.java",
]


def replace_in_file(path: Path) -> bool:
    try:
        txt = path.read_text(encoding="utf-8")
    except OSError:
        return False
    orig = txt
    for old, new in REPLACEMENTS_CONTENT:
        txt = txt.replace(old, new)
    if txt != orig:
        path.write_text(txt, encoding="utf-8", newline="\n")
        return True
    return False


def walk_replace(root: Path) -> int:
    n = 0
    if not root.exists():
        return 0
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith(".java") or fn.endswith(".yml") or fn.endswith(".yaml") or fn.endswith(".md"):
                p = Path(dirpath) / fn
                if replace_in_file(p):
                    n += 1
    return n


def safe_move(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists():
        raise SystemExit(f"Refuse overwrite: {dst}")
    shutil.move(str(src), str(dst))


def move_tree_contents(src_dir: Path, dst_dir: Path) -> None:
    if not src_dir.is_dir():
        return
    dst_dir.mkdir(parents=True, exist_ok=True)
    for item in sorted(src_dir.iterdir()):
        target = dst_dir / item.name
        if target.exists():
            raise SystemExit(f"Refuse overwrite moving {item} -> {target}")
        shutil.move(str(item), str(target))


def main() -> None:
    os.chdir(ROOT)

    for rel in DELETE_REL:
        p = MAIN / rel
        if p.exists():
            p.unlink()

    # Upload: relocate from demo packages (content patch happens in global pass)
    u_ctrl = MAIN / "controller/demo/UploadController.java"
    if u_ctrl.exists():
        safe_move(u_ctrl, MAIN / "controller/UploadController.java")
    u_svc = MAIN / "service/demo/UploadService.java"
    if u_svc.exists():
        safe_move(u_svc, MAIN / "service/UploadService.java")
    u_impl = MAIN / "service/serviceImpl/demo/UploadServiceImpl.java"
    if u_impl.exists():
        safe_move(u_impl, MAIN / "service/serviceImpl/UploadServiceImpl.java")
    u_map = MAIN / "mapper/demo/UploadMapper.java"
    if u_map.exists():
        safe_move(u_map, MAIN / "mapper/UploadMapper.java")

    # Bulk content replace (whole repo Java under src + key roots)
    for base in [
        ROOT / "src/main/java",
        ROOT / "src/test/java",
    ]:
        walk_replace(base)

    # Physical package dirs
    move_tree_contents(MAIN / "mapper/dp", MAIN / "mapper")
    shutil.rmtree(MAIN / "mapper/dp", ignore_errors=True)

    move_tree_contents(MAIN / "entity/dp", MAIN / "entity")
    shutil.rmtree(MAIN / "entity/dp", ignore_errors=True)

    move_tree_contents(MAIN / "controller/dp", MAIN / "controller")
    shutil.rmtree(MAIN / "controller/dp", ignore_errors=True)

    move_tree_contents(MAIN / "service/dp", MAIN / "service")
    shutil.rmtree(MAIN / "service/dp", ignore_errors=True)

    move_tree_contents(MAIN / "service/serviceImpl/dp", MAIN / "service/serviceImpl")
    shutil.rmtree(MAIN / "service/serviceImpl/dp", ignore_errors=True)

    dp_root = MAIN / "dp"
    if (dp_root / "quickmatch").is_dir():
        move_tree_contents(dp_root / "quickmatch", MAIN / "quickmatch")
        shutil.rmtree(dp_root / "quickmatch", ignore_errors=True)

    if (dp_root / "presence").is_dir():
        move_tree_contents(dp_root / "presence", MAIN / "presence")
        shutil.rmtree(dp_root / "presence", ignore_errors=True)

    shutil.rmtree(dp_root, ignore_errors=True)

    if (MAIN / "utils/dp").is_dir():
        move_tree_contents(MAIN / "utils/dp", MAIN / "utils")
        shutil.rmtree(MAIN / "utils/dp", ignore_errors=True)

    # Tests: mapper/dp and dp/quickmatch
    if TEST.is_dir():
        t_mapper = TEST / "mapper/dp"
        if t_mapper.is_dir():
            move_tree_contents(t_mapper, TEST / "mapper")
            shutil.rmtree(t_mapper, ignore_errors=True)
        t_qp = TEST / "dp/quickmatch"
        if t_qp.is_dir():
            TEST.mkdir(parents=True, exist_ok=True)
            dst_q = TEST / "quickmatch"
            dst_q.mkdir(parents=True, exist_ok=True)
            for item in t_qp.rglob("*"):
                if item.is_file():
                    rel = item.relative_to(t_qp)
                    target = dst_q / rel
                    target.parent.mkdir(parents=True, exist_ok=True)
                    if target.exists():
                        raise SystemExit(f"test file clash {target}")
                    shutil.move(str(item), str(target))
            shutil.rmtree(TEST / "dp", ignore_errors=True)

        svc_dp = TEST / "service/dp"
        if svc_dp.is_dir():
            move_tree_contents(svc_dp, TEST / "service")
            shutil.rmtree(svc_dp, ignore_errors=True)

        svc_impl_dp = TEST / "service/serviceImpl/dp"
        if svc_impl_dp.is_dir():
            move_tree_contents(svc_impl_dp, TEST / "service/serviceImpl")
            shutil.rmtree(svc_impl_dp, ignore_errors=True)

    walk_replace(TEST)
    walk_replace(ROOT / "src/main/java")

    # Prune empty demo dirs
    for d in [
        MAIN / "controller/demo",
        MAIN / "service/demo",
        MAIN / "service/serviceImpl/demo",
        MAIN / "mapper/demo",
        MAIN / "entity/demo",
    ]:
        if d.exists() and not any(d.iterdir()):
            d.rmdir()

    jwt = MAIN / "security/JwtSecurityConstants.java"
    if jwt.exists():
        t = jwt.read_text(encoding="utf-8").replace(
            '            "/demo/redis/**",\n', ""
        )
        jwt.write_text(t, encoding="utf-8")

    print("flatten script done")


if __name__ == "__main__":
    main()
