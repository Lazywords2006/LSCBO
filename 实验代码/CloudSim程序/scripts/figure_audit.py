"""Verify raw values, vector paths, and legends in experimental result plots."""
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
from matplotlib.path import Path as MplPath
from matplotlib.transforms import Bbox

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/"实验数据/CloudSim"

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def verify_figures():
    raw=pd.read_csv(OUT/'data/冷启动_3990行.csv',dtype=str)
    report=[];styles={};errors=[];positions=[]
    for name in ['Fig6','Fig7']:
        folder=OUT/'figures';layout=json.loads((folder/(name+'_layout.json')).read_text(encoding='utf-8'))
        assert layout['data_sha256']==sha(OUT/'data/冷启动_3990行.csv') and layout['absolute_cost_zoom'] is None
        xml=ET.parse(folder/(name+'.svg')).getroot();ns={'s':'http://www.w3.org/2000/svg'}
        doc=fitz.open(folder/(name+'.pdf'));assert len(doc)==1 and not doc[0].get_images()
        drawings=doc[0].get_drawings()
        text=doc[0].get_text();assert 'Greedy' not in text and 'Zoom' not in text
        assert len(layout['panels'])==(1 if name=='Fig6' else 4)
        if name=='Fig6':
            assert '(b)' not in text and 'Final scalar objective' not in text
            assert layout['panel_title_position']=='none'
            assert layout['panels'][0]['y_ticks']==[3,5,10,20,30,50]
            tick_groups=[node for node in xml.findall('.//s:g',ns) if re.fullmatch(r'ytick_\d+',node.attrib.get('id',''))]
            assert [float(''.join(node.find('.//s:text',ns).itertext())) for node in tick_groups]==[3,5,10,20,30,50]
        else:
            assert '(c) Cost' in text and 'Cost excess' not in text
            assert [p['ylabel'] for p in layout['panels']]==['Makespan','Energy','Cost','Load imbalance']
            assert not any(unit in text for unit in ['(s)','(Wh)','(units)'])
            assert re.search(r'^Cost$',text,re.M)
        assert layout['each_panel_has_legend']
        for panel in layout['panels']:
            assert panel['legend_boxed'] and panel['covered_data_artists']==0
            positions.append(panel['legend_position'])
            axes_box=np.array(panel['axes_bbox_from_bottom_pt'])
            legend_box=np.array(panel['legend_bbox_from_bottom_pt'])
            assert axes_box[0]<=legend_box[0]<legend_box[2]<=axes_box[2]
            assert axes_box[1]<=legend_box[1]<legend_box[3]<=axes_box[3]
            anchor_x=(12.-2.625)/(axes_box[2]-axes_box[0])
            assert np.allclose(panel['legend_anchor'],[anchor_x,1.],rtol=0,atol=1e-12)
            assert panel['legend_position']=='upper left'
            assert panel['legend_rows']==2 and panel['legend_columns']==3
            assert abs(legend_box[0]-axes_box[0]-12.)<1e-4
            assert panel['axis_label_fontsize_pt']==9.5 and panel['tick_label_fontsize_pt']==8.5
            box=panel['caption_bbox_from_bottom_pt']
            if name=='Fig6':
                assert box is None and panel['title'] is None
            else:
                assert panel['caption_fontsize_pt']==12.
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
        # Read back every independent legend, including all four Fig7 panels.
        methods=['LSCBO','WOA','PSO','CBO','AOA','GWO']
        labels=['LSCBO','WOA','PSO','CBO','AOA','GWO']
        for index,panel in enumerate(layout['panels']):
            assert panel['legend_labels']==labels
            group=xml.find(f".//s:g[@id='{panel['legend_gid']}']",ns)
            assert group is not None
            actual_labels=[''.join(node.itertext()) for node in group.findall('.//s:text',ns)]
            assert actual_labels==labels
            frame=group.find('s:g/s:path',ns)
            assert frame is not None and 'Q' not in frame.attrib['d']
            assert 'fill: #ffffff' in frame.attrib['style'] and 'stroke: #6d7380' in frame.attrib['style']
            bottom_box=panel['legend_bbox_from_bottom_pt']
            top_box=[bottom_box[0],doc[0].rect.height-bottom_box[3],bottom_box[2],doc[0].rect.height-bottom_box[1]]
            for method in methods:
                line_group=xml.find(f".//s:g[@id='legend_line_{name}_{index}_{method}']",ns)
                path=line_group.find('s:path',ns)
                props={a.strip():b.strip() for a,b in (v.strip().split(':',1) for v in path.attrib['style'].split(';') if ':' in v)}
                assert props['stroke']==styles[method]['color'].lower() and 'stroke-dasharray' not in props
                assert abs(float(props.get('stroke-width',1))-styles[method]['linewidth'])<1e-9
                color=tuple(int(styles[method]['color'][i:i+2],16)/255 for i in [1,3,5])
                actual=[d for d in drawings if d.get('color') and np.allclose(d['color'],color,rtol=0,atol=1e-6)
                        and len(d['items'])<=3 and all(item[0]=='l' for item in d['items'])
                        and d['rect'].x0>=top_box[0] and d['rect'].x1<=top_box[2]
                        and d['rect'].y0>=top_box[1] and d['rect'].y1<=top_box[3]]
                assert len(actual)==1 and abs(actual[0]['width']-styles[method]['linewidth'])<1e-6
                assert actual[0]['dashes'].replace(' ','')=='[]0'
            for curve in layout['curves']:
                if curve['panel']==index:
                    actual_points=np.array(re.findall(r'[ML]\s+([-+0-9.e]+)\s+([-+0-9.e]+)',
                        xml.find(f".//s:g[@id='{curve['gid']}']/s:path",ns).attrib['d']),float)
                    assert not MplPath(actual_points).intersects_bbox(Bbox.from_extents(*top_box),filled=False)
        report.append({'name':name,'vector_curves':len(layout['curves']),'data_points':10*len(layout['curves']),
                       'independent_legends':len(layout['panels']),'all_legends_boxed':True,
                       'legend_rows':2,'legend_columns':3,
                       'legend_position':positions[-1],'legend_matches_curves':True,'legend_data_overlaps':0,
                       'sha256':sha(folder/(name+'.pdf'))})
    folder=OUT/'figures';fig8=fitz.open(folder/'Fig8.pdf');assert len(fig8)==1 and not fig8[0].get_images()
    layout=json.loads((folder/'Fig8_layout.json').read_text(encoding='utf-8'))
    positions.append(layout['legend_position'])
    assert layout['each_panel_has_legend'] and layout['legend_boxed'] and layout['covered_data_artists']==0
    axes_box=np.array(layout['axes_bbox_from_bottom_pt']);legend_box=np.array(layout['legend_bbox_from_bottom_pt'])
    assert axes_box[0]<=legend_box[0]<legend_box[2]<=axes_box[2]
    assert axes_box[1]<=legend_box[1]<legend_box[3]<=axes_box[3]
    assert layout['legend_anchor']==[.08,1.] and layout['legend_position']=='upper left'
    assert layout['legend_rows']==1 and layout['legend_columns']==2
    assert abs(legend_box[0]-axes_box[0]-.08*(axes_box[2]-axes_box[0])-2.975)<1e-4
    expected_profiles=json.loads((OUT/'data/图表与统计汇总/analysis.json').read_text(encoding='utf-8'))['profiles']
    assert layout['profiles']==expected_profiles and len(layout['bars'])==8
    xml=ET.parse(folder/'Fig8.svg').getroot();drawings=fig8[0].get_drawings()
    pixmap=fig8[0].get_pixmap(matrix=fitz.Matrix(4,4),alpha=False)
    pixels=np.frombuffer(pixmap.samples,dtype=np.uint8).reshape(pixmap.height,pixmap.width,pixmap.n)
    top_box=[legend_box[0],fig8[0].rect.height-legend_box[3],legend_box[2],fig8[0].rect.height-legend_box[1]]
    for bar,profile in zip(layout['bars'],expected_profiles):
        assert bar['mean']==profile['mean']
        path=xml.find(f".//s:g[@id='{bar['gid']}']/s:path",ns)
        points=np.array(re.findall(r'[ML]\s+([-+0-9.e]+)\s+([-+0-9.e]+)',path.attrib['d']),float)
        expected=np.array(bar['expected_vector_points_pt'])[:4]
        assert points.shape==(4,2) and np.max(abs(points-expected))<1e-5
        assert not MplPath(np.vstack([points,points[0]])).intersects_bbox(Bbox.from_extents(*top_box),filled=True)
        bounds=[expected[:,0].min(),expected[:,1].min(),expected[:,0].max(),expected[:,1].max()]
        color=tuple(int(bar['color'][i:i+2],16)/255 for i in [1,3,5])
        if bar['profile_index']<5:
            # Hatched fills are clipped PDF patterns; their visible bar outline
            # provides exact geometry, while the SVG pattern and rendered PDF
            # independently verify the painted background color.
            pattern_id=re.search(r'fill:\s*url\(#([^)]*)\)',path.attrib['style']).group(1)
            background=xml.find(f".//s:pattern[@id='{pattern_id}']/s:rect",ns)
            assert background.attrib['fill']==bar['color'].lower()
            matches=[d for d in drawings if d.get('color') and np.allclose(d['color'],(1,1,1),rtol=0,atol=1e-6)
                     and np.max(abs(np.array(d['rect'])-bounds))<1e-4]
            x=int((bounds[0]+.45*(bounds[2]-bounds[0]))*4);y=int((bounds[1]+bounds[3])/2*4)
            region=pixels[y-6:y+7,x-6:x+7,:3].astype(int)
            assert np.any(np.max(abs(region-np.rint(np.array(color)*255)),axis=2)<3)
        else:
            matches=[d for d in drawings if d.get('fill') and np.allclose(d['fill'],color,rtol=0,atol=1e-6)
                     and np.max(abs(np.array(d['rect'])-bounds))<1e-4]
        assert len(matches)>=1
    legend=xml.find(f".//s:g[@id='{layout['legend_gid']}']",ns)
    assert [''.join(n.itertext()) for n in legend.findall('.//s:text',ns)]==layout['legend_labels']
    frame=legend.find('s:g/s:path',ns)
    assert 'Q' not in frame.attrib['d'] and 'fill: #ffffff' in frame.attrib['style'] and 'stroke: #6d7380' in frame.attrib['style']
    assert len(set(positions))==1 and positions[0]==json.loads((folder/'Fig7_layout.json').read_text(encoding='utf-8'))['panels'][3]['legend_position']
    report.append({'name':'Fig8','raster_images':0,'data_bars_verified':8,'independent_legends':1,
                   'legend_position':positions[-1],'all_legends_boxed':True,'legend_data_overlaps':0,
                   'legend_rows':1,'legend_columns':2,
                   'sha256':sha(folder/'Fig8.pdf')})
    return report,max(errors)
