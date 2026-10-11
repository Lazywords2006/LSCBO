"""Check Fig7 typography, actual vectors, legends and numerical values across result plots."""
from pathlib import Path
import argparse,hashlib,json,re,xml.etree.ElementTree as ET
import fitz
import numpy as np
from matplotlib.path import Path as MplPath
from matplotlib.transforms import Bbox
from generate_cec_figures import load_means,METHODS,STYLES
from verify_line_identity import identity,check_curve_and_legend,check_pdf_stroke

ROOT=Path(__file__).resolve().parents[1]
NS={'s':'http://www.w3.org/2000/svg'}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def vertices(path):return np.array(re.findall(r'[ML]\s+([-+0-9.e]+)\s+([-+0-9.e]+)',path.attrib['d']),float)
def props(path):return dict(item.strip().split(':',1) for item in path.attrib.get('style','').split(';') if ':' in item)
def font_sizes(node):
    return [float(value) for text in node.findall('.//s:text',NS)
            for value in re.findall(r'([0-9.]+)px',text.attrib.get('style',''))]
def check_text(doc):
    for block in doc[0].get_text('dict')['blocks']:
        for line in block.get('lines',[]):
            for span in line['spans']:
                if not span['text'].strip():continue
                rect=fitz.Rect(span['bbox'])
                assert rect.x0>=-.5 and rect.y0>=-.5 and rect.x1<=doc[0].rect.width+.5 and rect.y1<=doc[0].rect.height+.5,(span['text'],rect)

def check_panel(panel,xml,doc,index):
    axes=np.array(panel['axes_bbox_from_bottom_pt']);box=np.array(panel['legend_bbox_from_bottom_pt'])
    assert abs(box[0]-axes[0]-12.)<1e-4 and abs(axes[3]-box[3]-2.625)<1e-4
    assert panel['axis_label_fontsize_pt']==9.5 and panel['tick_label_fontsize_pt']==8.5
    assert panel['legend_rows']==2 and panel['legend_position']=='upper left' and panel['covered_data_artists']==0
    group=xml.find(f".//s:g[@id='{panel['legend_gid']}']",NS);assert group is not None
    assert all(size==7.5 for size in font_sizes(group))
    labels=[''.join(t.itertext()) for t in group.findall('.//s:text',NS)]
    assert labels==panel['legend_labels']
    frame=group.find('s:g/s:path',NS);assert 'Q' not in frame.attrib['d']
    style=props(frame);assert style['fill'].strip()=='#ffffff' and style['stroke'].strip()=='#6d7380'
    assert abs(float(style['stroke-width'])-.65)<1e-8
    actual=vertices(frame);expected=np.array([box[0],doc[0].rect.height-box[3],box[2],doc[0].rect.height-box[1]])
    bounds=np.array([actual[:,0].min(),actual[:,1].min(),actual[:,0].max(),actual[:,1].max()])
    # Left/top anchors are physical offsets; text metrics can differ slightly between Agg and vector renderers.
    assert np.max(abs(bounds[:2]-expected[:2]))<1e-4
    assert np.max(abs(bounds[2:]-expected[2:]))<.5
    color=tuple(int('6D7380'[i:i+2],16)/255 for i in [0,2,4])
    frames=[d for d in doc[0].get_drawings() if d.get('color') and d.get('fill')
            and np.allclose(d['color'],color,rtol=0,atol=1e-6) and np.allclose(d['fill'],(1,1,1),rtol=0,atol=1e-6)
            and np.max(abs(np.array(d['rect'])[:2]-bounds[:2]))<1e-4]
    assert len(frames)==1 and abs(frames[0]['width']-.65)<1e-6
    pdf_box=np.array(frames[0]['rect'])
    assert pdf_box[2]<=axes[2]+1e-4 and pdf_box[3]<=doc[0].rect.height-axes[1]+1e-4
    for block in doc[0].get_text('dict')['blocks']:
        for line in block.get('lines',[]):
            for span in line['spans']:
                if fitz.Rect(*pdf_box).contains(fitz.Rect(span['bbox'])):
                    assert abs(span['size']-7.5)<1e-6
    title=panel.get('title')
    if title:
        assert panel['caption_fontsize_pt']==12.
        captions=[t for t in xml.findall('.//s:text',NS) if ''.join(t.itertext())==title]
        assert len(captions)==1 and re.search(r'12(?:\.0)?px',captions[0].attrib['style'])
    for tick in xml.findall('.//s:g',NS):
        if re.fullmatch(r'[xy]tick_\d+',tick.attrib.get('id','')):
            assert all(size==8.5 for size in font_sizes(tick))
    # Actual axis-label SVG fonts, separately from tick glyphs.
    for axis in xml.findall('.//s:g',NS):
        if re.fullmatch(r'matplotlib.axis_\d+',axis.attrib.get('id','')):
            assert set(font_sizes(axis))<={8.5,9.5}
    return bounds,pdf_box

def verify_cec(folder,identities):
    means=load_means(ROOT/'实验数据/数据');summary=means.groupby(['evaluations','algorithm'])['rank'].mean()
    final=means[means.evaluations.eq(300000)].groupby('algorithm')['rank'].mean()
    names=['Fig1','Fig2','Fig3',*[f'Fig3_F{n}' for n in [1,3,5,10,15,20]]]
    results=[];max_error=0.
    for name in names:
        layout=json.loads((folder/f'{name}_layout.json').read_text(encoding='utf-8'))
        assert layout['data_sha256']==sha(ROOT/'实验数据/数据/cec2017_checkpoint_means.csv')
        xml=ET.parse(folder/f'{name}.svg').getroot();doc=fitz.open(folder/f'{name}.pdf')
        assert len(doc)==1 and not doc[0].get_images();check_text(doc)
        drawings=doc[0].get_drawings();boxes=[]
        for index,panel in enumerate(layout['panels']):
            boxes.append(check_panel(panel,xml,doc,index));assert panel['legend_columns']==5
        for curve in layout['curves']:
            alg=curve['algorithm'];function=curve['function']
            if alg in identities:assert identities[alg]==identity(curve)
            identities[alg]=identity(curve)
            check_curve_and_legend(xml,curve,name,drawings)
            values=summary.xs(alg,level='algorithm').sort_index().to_numpy() if function is None else means[means.function.eq(function)&means.algorithm.eq(alg)].sort_values('evaluations').mean_error.to_numpy()
            assert np.allclose(curve['y'],values,rtol=0,atol=1e-10) and curve['x']==list(range(3,301,3))
            group=xml.find(f".//s:g[@id='{curve['gid']}']",NS);path=group.find('s:path',NS)
            actual=vertices(path);expected=np.array(curve['expected_vector_points_pt'])
            assert actual.shape==(100,2) and np.max(abs(actual-expected))<1e-5
            assert not MplPath(actual).intersects_bbox(Bbox.from_extents(*boxes[curve['panel']][0]),filled=False)
            assert not MplPath(expected).intersects_bbox(Bbox.from_extents(*boxes[curve['panel']][1]),filled=False),curve['gid']
            style=props(path);assert style['stroke'].strip()==STYLES[alg][0].lower()
            assert float(style.get('stroke-width','1'))==STYLES[alg][2]
            dashes=curve['dash_pattern'][1]
            if dashes:
                assert np.allclose([float(v) for v in style['stroke-dasharray'].split(',')],dashes,rtol=0,atol=1e-8)
            else:assert 'stroke-dasharray' not in style
            color=tuple(int(curve['color'][i:i+2],16)/255 for i in [1,3,5]);matches=[]
            for drawing in drawings:
                if not drawing.get('color') or not np.allclose(drawing['color'],color,rtol=0,atol=1e-6):continue
                segments=[item for item in drawing['items'] if item[0]=='l']
                points=np.array([[*segments[0][1]],*[[*item[2]] for item in segments]]) if segments else np.empty((0,2))
                if points.shape==expected.shape and np.max(abs(points-expected))<1e-4:
                    check_pdf_stroke(drawing,curve);matches.append(drawing);max_error=max(max_error,float(np.max(abs(points-expected))))
            assert len(matches)==1,curve['gid']
            legend_line=xml.find(f".//s:g[@id='legend_line_{name}_{curve['panel']}_{alg}']/s:path",NS)
            assert props(legend_line)['stroke'].strip()==style['stroke'].strip()
            assert props(legend_line).get('stroke-dasharray')==style.get('stroke-dasharray')
        for point in layout['points']:
            assert np.allclose(point['x'],[final[point['algorithm']]],rtol=0,atol=1e-12)
            use=xml.find(f".//s:g[@id='{point['gid']}']//s:use",NS)
            actual=np.array([[float(use.attrib['x']),float(use.attrib['y'])]])
            assert np.max(abs(actual-np.array(point['expected_vector_points_pt'])))<1e-5
            assert props(use)['stroke'].strip()==STYLES[point['algorithm']][0].lower()
            assert props(use)['fill'].strip()==STYLES[point['algorithm']][0].lower()
        results.append({'name':name,'panels':len(layout['panels']),'curve_points':len(layout['curves'])*100,
                        'actual_PDF_and_SVG_verified':True,'sha256':sha(folder/f'{name}.pdf')})
    return results,max_error

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cec',type=Path,default=ROOT/'实验数据/CEC2017/figures')
    parser.add_argument('--cloud',type=Path,default=ROOT/'实验数据/CloudSim/figures')
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args();identities={};cec,error=verify_cec(args.cec,identities)
    for name in ['Fig6','Fig7','Fig8']:
        layout=json.loads((args.cloud/f'{name}_layout.json').read_text(encoding='utf-8'))
        xml=ET.parse(args.cloud/f'{name}.svg').getroot();doc=fitz.open(args.cloud/f'{name}.pdf');check_text(doc)
        panels=layout['panels'] if name!='Fig8' else [layout]
        for index,panel in enumerate(panels):check_panel(panel,xml,doc,index)
        for curve in layout.get('curves',[]):
            assert identities[curve['algorithm']]==identity(curve)
            check_curve_and_legend(xml,curve,name,doc[0].get_drawings())
    report={'all_checks_passed':True,'CEC2017':cec,'axis_label_fontsize_pt':9.5,'tick_label_fontsize_pt':8.5,
            'panel_title_fontsize_pt':12,'legend_fontsize_pt':7.5,'legend_rows':2,
            'legend_left_margin_pt':12,'legend_top_margin_pt':2.625,'max_CEC_vector_error_pt':error,
            'same_algorithm_color_dash_width_and_marker_across_CEC_and_cloud':True,'algorithm_styles':identities}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({'all_checks_passed':True,'cec_plots':len(cec),'cec_curve_points':sum(r['curve_points'] for r in cec),'max_vector_error_pt':error},indent=2))
if __name__=='__main__':main()
