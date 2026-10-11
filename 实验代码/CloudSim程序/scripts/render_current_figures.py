"""Re-render Fig.6–8 from published data into an external output directory."""
from pathlib import Path
import argparse
import json
import os
import sys

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,required=True)
    args=p.parse_args()
    args.out=args.out.resolve();args.out.mkdir(parents=True,exist_ok=True)
    root=Path(__file__).resolve().parents[3]
    published=root/'论文/Cost与云调度两个板块'
    if args.out==published/'图':p.error('Use an external output directory to preserve published artifacts')
    os.environ['LSCBO_RUNTIME']=str(args.out)
    os.environ['MPLCONFIGDIR']=str(args.out/'matplotlib')
    os.environ['TMP']=os.environ['TEMP']=str(args.out)
    import plot_fig6_fig7 as plots
    import analysis_helpers as helper
    sys.argv=['plots','--data',str(published/'数据/冷启动_3990行.csv'),'--out',str(args.out),
              '--figure','all','--cost-display','absolute']
    plots.main()
    analysis=json.loads((published/'数据/图表与统计汇总/analysis.json').read_text(encoding='utf-8'))
    helper.draw_fig8(analysis['profiles'],args.out)
    print('Rendered:',args.out)

if __name__=='__main__':main()
