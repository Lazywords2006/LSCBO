#!/bin/zsh
cd "$(dirname "$0")"
python3 scripts/run_lscbo_full_program.py --profile formal --seed-start 43 --seed-end 72 --batch-size 1 --experiments all
echo
echo "正式全量实验已结束。按回车关闭窗口。"
read
