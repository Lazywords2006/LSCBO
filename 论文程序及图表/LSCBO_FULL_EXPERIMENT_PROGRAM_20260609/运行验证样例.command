#!/bin/zsh
cd "$(dirname "$0")"
python3 scripts/run_lscbo_full_program.py --profile verify --seed-start 43 --batch-size 1 --experiments all
python3 scripts/run_lscbo_full_program.py --profile smoke-cloudsim --out results/verify_cloudsim_smoke
echo
echo "验证样例已结束。按回车关闭窗口。"
read
