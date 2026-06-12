#!/bin/zsh
cd "$(dirname "$0")"
python3 scripts/run_lscbo_full_program.py --profile formal-cloudsim --seed-start 43 --seed-end 72 --batch-size 1 --experiments all
echo
echo "CloudSim 全量校验已结束。按回车关闭窗口。"
read
