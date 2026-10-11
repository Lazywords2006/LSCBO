"""Read back values, vector paths, manuscript text, and the delivery archive."""
from pathlib import Path
import hashlib
import json
import re
import zipfile
import xml.etree.ElementTree as ET
from decimal import Decimal,localcontext
import fitz
import numpy as np
import pandas as pd

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/"论文/Cost与云调度两个板块"

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def verify_figures():
    raw=pd.read_csv(OUT/'数据/冷启动_3990行.csv',dtype=str)
    report=[];styles={};errors=[]
    for name in ['Fig6','Fig7']:
        folder=OUT/'图';layout=json.loads((folder/(name+'_layout.json')).read_text(encoding='utf-8'))
        assert layout['data_sha256']==sha(OUT/'数据/冷启动_3990行.csv') and layout['absolute_cost_zoom'] is None
        xml=ET.parse(folder/(name+'.svg')).getroot();ns={'s':'http://www.w3.org/2000/svg'}
        doc=fitz.open(folder/(name+'.pdf'));assert len(doc)==1 and not doc[0].get_images()
        drawings=doc[0].get_drawings()
        text=doc[0].get_text();assert 'Greedy' not in text and 'Zoom' not in text
        assert len(layout['panels'])==(1 if name=='Fig6' else 4)
        if name=='Fig6':assert '(b)' not in text
        else:assert '(c) Batch reservation cost' in text and 'Cost excess' not in text
        for panel in layout['panels']:
            box=panel['caption_bbox_from_bottom_pt']
            assert abs((box[0]+box[2])/2-panel['panel_center_x_pt'])<1e-4
            # The caption is below the axes in bottom-origin coordinates.
            assert box[3] < float(doc[0].rect.height)-panel['panel_bottom_from_top_pt']
        with localcontext() as ctx:
            ctx.prec=40
            for curve in layout['curves']:
                metric='objective' if name=='Fig6' else ['makespan','energy','cost','imbalance'][curve['panel']]
                style={k:curve[k] for k in ['color','linewidth','linestyle','marker']}
                if curve['algorithm'] in styles:assert styles[curve['algorithm']]==style
                styles[curve['algorithm']]=style
                assert style['linestyle']=='-' and style['marker']=='None'
                for n,value in zip(curve['x'],curve['y']):
                    block=raw[raw.taskCount.eq(str(n))&raw.algorithm.eq(curve['algorithm'])]
                    assert len(block)==30 and set(block.seed)=={str(i) for i in range(43,73)}
                    mean=sum(map(Decimal,block[metric]))/Decimal(30)
                    assert np.isclose(value,float(mean),rtol=1e-13,atol=1e-13)
                group=xml.find(f".//s:g[@id='{curve['gid']}']",ns);path=group.find('s:path',ns)
                vertices=np.array(re.findall(r'[ML]\s+([-+0-9.e]+)\s+([-+0-9.e]+)',path.attrib['d']),float)
                expected=np.asarray(curve['expected_vector_points_pt'])
                assert vertices.shape==(10,2) and np.max(abs(vertices-expected))<1e-5
                props={a.strip():b.strip() for a,b in (x.strip().split(':',1) for x in path.attrib['style'].split(';') if ':' in x)}
                assert props['stroke']==curve['color'].lower() and 'stroke-dasharray' not in props
                assert abs(float(props.get('stroke-width',1))-curve['linewidth'])<1e-9
                color=tuple(int(curve['color'][i:i+2],16)/255 for i in [1,3,5]);matches=[]
                for drawing in drawings:
                    items=drawing['items']
                    if not drawing.get('color') or len(items)!=9 or not all(i[0]=='l' for i in items):continue
                    points=np.array([list(items[0][1])]+[list(i[2]) for i in items])
                    if np.allclose(drawing['color'],color,rtol=0,atol=1e-6) and np.max(abs(points-expected))<1e-4:
                        assert abs(drawing['width']-curve['linewidth'])<1e-6
                        assert drawing['dashes'].replace(' ','')=='[]0';matches.append(float(np.max(abs(points-expected))))
                assert len(matches)==1;errors.extend(matches)
        # Legend uses actual curves; also inspect its own output paths.
        legends=xml.findall(".//s:g[@id='legend_1']//s:path",ns)
        legend_styles=[]
        for path in legends:
            props={a.strip():b.strip() for a,b in (x.strip().split(':',1) for x in path.attrib.get('style','').split(';') if ':' in x)}
            if props.get('fill')=='none' and 'stroke' in props:
                legend_styles.append((props['stroke'],float(props.get('stroke-width',1))))
        for method in ['LSCBO','WOA','PSO','CBO','AOA','GWO']:
            expected=(styles[method]['color'].lower(),styles[method]['linewidth'])
            assert expected in legend_styles
        report.append({'name':name,'vector_curves':len(layout['curves']),'data_points':10*len(layout['curves']),
                       'legend_matches_curves':True,'sha256':sha(folder/(name+'.pdf'))})
    fig8=fitz.open(OUT/'图/Fig8.pdf');assert len(fig8)==1 and not fig8[0].get_images()
    report.append({'name':'Fig8','raster_images':0,'sha256':sha(OUT/'图/Fig8.pdf')})
    return report,max(errors)
