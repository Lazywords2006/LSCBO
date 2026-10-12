"""Read back algorithm stroke/marker identities from actual SVG and PDF assets."""
import re
import numpy as np
from matplotlib.lines import Line2D
from matplotlib.markers import MarkerStyle
from matplotlib.path import Path
from matplotlib.transforms import Affine2D
from figure_style import algorithm_line

NS={'s':'http://www.w3.org/2000/svg'}
NUMBER=r'[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?'
STYLE_KEYS=['color','linewidth','linestyle','dash_pattern','marker','markersize',
            'markerfacecolor','markeredgecolor','markeredgewidth']

def props(path):
    return {a.strip():b.strip() for a,b in (item.split(':',1) for item in path.attrib.get('style','').split(';') if ':' in item)}

def identity(curve):
    return {key:curve[key] for key in STYLE_KEYS}

def check_recipe(curve):
    reference=Line2D([],[],**algorithm_line(curve['algorithm']))
    expected={'color':reference.get_color(),'linewidth':reference.get_linewidth(),
              'linestyle':reference.get_linestyle(),'dash_pattern':reference._dash_pattern,
              'marker':reference.get_marker(),'markersize':reference.get_markersize(),
              'markerfacecolor':reference.get_markerfacecolor(),'markeredgecolor':reference.get_markeredgecolor(),
              'markeredgewidth':reference.get_markeredgewidth()}
    for key in STYLE_KEYS:
        actual=curve[key];value=expected[key]
        if key=='dash_pattern':
            assert actual[0]==value[0]
            assert (actual[1] is None)==(value[1] is None)
            if value[1] is not None:assert np.allclose(actual[1],value[1],rtol=0,atol=1e-10)
        else:assert actual==value,(curve['gid'],key,actual,value)

def check_svg_stroke(path,curve):
    style=props(path)
    assert style['stroke']==curve['color'].lower()
    assert abs(float(style.get('stroke-width',1))-curve['linewidth'])<1e-8
    offset,dashes=curve['dash_pattern']
    if dashes:
        assert np.allclose(list(map(float,re.findall(NUMBER,style['stroke-dasharray']))),dashes,rtol=0,atol=1e-8)
        assert abs(float(style.get('stroke-dashoffset',0))-offset)<1e-8
    else:assert 'stroke-dasharray' not in style

def check_pdf_stroke(drawing,curve):
    assert abs(drawing['width']-curve['linewidth'])<1e-6
    values=list(map(float,re.findall(NUMBER,drawing['dashes'])))
    offset,dashes=curve['dash_pattern']
    assert np.allclose(values,[*(dashes or []),offset],rtol=0,atol=1e-4)

def drawing_bounds(drawing):
    # MuPDF's reported rect omits some MOVETO starts in disconnected +/x
    # markers. Include every actual segment endpoint/control point instead.
    points=[]
    for item in drawing['items']:
        if item[0] in ['l','c']:points.extend(item[1:])
        elif item[0]=='re':points.extend([item[1].tl,item[1].br])
        elif item[0]=='qu':points.extend([item[1].ul,item[1].ur,item[1].ll,item[1].lr])
    points=np.asarray(points)
    return np.array([points[:,0].min(),points[:,1].min(),points[:,0].max(),points[:,1].max()])

def check_markers(xml,group,curve,drawings,centers=None,pdf_centers=None):
    uses=group.findall('.//s:use',NS)
    if curve['marker'] in [None,'None','']:
        assert not uses,curve['gid']
        return 0
    actual=np.array([[float(u.attrib['x']),float(u.attrib['y'])] for u in uses])
    if centers is None:centers=np.array(curve['expected_marker_points_pt'])
    else:centers=np.asarray(centers)
    assert actual.shape==centers.shape and np.max(abs(actual-centers))<1e-5,curve['gid']
    marker=MarkerStyle(curve['marker'])
    shape=marker.get_path().transformed(marker.get_transform()).transformed(Affine2D().scale(curve['markersize'],-curve['markersize']))
    expected=shape.vertices[shape.codes!=Path.CLOSEPOLY] if shape.codes is not None else shape.vertices
    rgb=tuple(int(curve['color'][i:i+2],16)/255 for i in [1,3,5])
    pdf_centers=centers if pdf_centers is None else pdf_centers
    for use,center,pdf_center in zip(uses,centers,pdf_centers):
        ref=use.attrib['{http://www.w3.org/1999/xlink}href'][1:]
        prototype=xml.find(f".//s:path[@id='{ref}']",NS)
        vertices=np.array(list(map(float,re.findall(NUMBER,prototype.attrib['d'])))).reshape(-1,2)
        assert vertices.shape==expected.shape and np.max(abs(vertices-expected))<1e-5,(curve['algorithm'],vertices,expected)
        style=props(use);assert style['stroke']==curve['color'].lower()
        assert abs(float(style.get('stroke-width',1))-curve['markeredgewidth'])<1e-8
        if marker.is_filled():assert style['fill']=='#ffffff'
        bounds=np.array([expected[:,0].min(),expected[:,1].min(),expected[:,0].max(),expected[:,1].max()])+np.tile(pdf_center,2)
        matches=[d for d in drawings if d.get('color') and np.allclose(d['color'],rgb,rtol=0,atol=1e-6)
                 and abs(d['width']-curve['markeredgewidth'])<1e-6 and np.max(abs(drawing_bounds(d)-bounds))<2e-4]
        assert len(matches)==1,(curve['gid'],center,len(matches))
        if marker.is_filled():assert matches[0].get('fill') and np.allclose(matches[0]['fill'],(1,1,1),rtol=0,atol=1e-6)
    return len(uses)

def check_curve_and_legend(xml,curve,name,drawings):
    check_recipe(curve)
    group=xml.find(f".//s:g[@id='{curve['gid']}']",NS)
    check_svg_stroke(group.find('s:path',NS),curve)
    count=check_markers(xml,group,curve,drawings)
    legend=xml.find(f".//s:g[@id='legend_line_{name}_{curve['panel']}_{curve['algorithm']}']",NS)
    path=legend.find('s:path',NS);check_svg_stroke(path,curve)
    points=np.array(re.findall(r'[ML]\s+('+NUMBER+r')\s+('+NUMBER+r')',path.attrib['d']),float)
    assert points.shape==(3,2)
    rgb=tuple(int(curve['color'][i:i+2],16)/255 for i in [1,3,5]);matches=[]
    for d in drawings:
        if not d.get('color') or not np.allclose(d['color'],rgb,rtol=0,atol=1e-6):continue
        items=d['items']
        if len(items)!=2 or not all(i[0]=='l' for i in items):continue
        actual=np.array([list(items[0][1]),*[list(i[2]) for i in items]])
        # Legend column widths depend on backend text metrics; the first anchor
        # is checked separately by the frame audit. Read its PDF line directly.
        if np.max(abs(actual[:,0]-points[:,0]))<.5 and np.max(abs(actual[:,1]-points[:,1]))<1.1:matches.append((d,actual))
    assert len(matches)==1,curve['gid']
    check_pdf_stroke(matches[0][0],curve)
    check_markers(xml,legend,curve,drawings,[points[1]],[matches[0][1][1]])
    return count
