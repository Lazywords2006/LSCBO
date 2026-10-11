"""Physical figure standard shared by CEC2017 and cloud plots, based on Fig7."""
from __future__ import annotations
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.collections import LineCollection, PathCollection, PolyCollection
from matplotlib.path import Path
from matplotlib.transforms import Bbox

WIDTH_INCHES=130.8/25.4
FIGURE_WIDTH_INCHES=WIDTH_INCHES*1.22
AXIS_LABEL_SIZE=9.5
TICK_LABEL_SIZE=8.5
PANEL_TITLE_SIZE=12.
LEGEND_FONT_SIZE=7.5
LEGEND_LEFT_MARGIN_PT=12.
LEGEND_TOP_MARGIN_PT=.35*LEGEND_FONT_SIZE
GRID_COLOR='#E4E4E4'
COLORS={'LSCBO':'#E8302F','CBO':'#5B4373','PSO':'#0072B2','GWO':'#2CA02C','AOA':'#A05A78','WOA':'#8A6420',
        'SA':'#56B4E9','HLBO':'#E69F00','GWCA':'#CC79A7','GREEDY_LPT':'#454545',
        'direct5400':'#56B4E9','direct6000':'#009E73','weights':'#D62728','vms':'#0072B2'}

def configure():
    plt.rcParams.update({'font.family':'DejaVu Sans','font.size':AXIS_LABEL_SIZE,
        'axes.labelsize':AXIS_LABEL_SIZE,'axes.titlesize':PANEL_TITLE_SIZE,
        'xtick.labelsize':TICK_LABEL_SIZE,'ytick.labelsize':TICK_LABEL_SIZE,
        'legend.fontsize':LEGEND_FONT_SIZE,'axes.linewidth':.8,
        'axes.spines.top':True,'axes.spines.right':True,'xtick.direction':'out','ytick.direction':'out',
        'xtick.major.width':.8,'ytick.major.width':.8,'pdf.fonttype':42,'ps.fonttype':42,
        'svg.fonttype':'none','savefig.facecolor':'white','svg.hashsalt':'lscbo-fig7-template-v1','path.simplify':False})

def grid(axis,which='both'):
    axis.grid(axis=which,color=GRID_COLOR,linewidth=.55);axis.set_axisbelow(True)
    axis.tick_params(length=2.6,pad=2.5,labelsize=TICK_LABEL_SIZE)

def legend_anchor(axis):
    width=axis.get_position().width*axis.figure.get_figwidth()*72
    return ((LEGEND_LEFT_MARGIN_PT-LEGEND_TOP_MARGIN_PT)/width,1.)

def covered_artists(axis,box,renderer):
    count=sum(line.get_path().transformed(line.get_transform()).intersects_bbox(box,filled=False) for line in axis.lines)
    count+=sum(patch.get_path().transformed(patch.get_transform()).intersects_bbox(box,filled=True) for patch in axis.patches)
    for collection in axis.collections:
        if isinstance(collection,PathCollection):
            points=collection.get_offset_transform().transform(collection.get_offsets())
            count+=sum(box.contains(*point) for point in points)
        elif isinstance(collection,LineCollection):
            count+=sum(Path(collection.get_transform().transform(segment)).intersects_bbox(box,filled=False) for segment in collection.get_segments())
        elif isinstance(collection,PolyCollection):
            count+=sum(path.transformed(collection.get_transform()).intersects_bbox(box,filled=True) for path in collection.get_paths())
    count+=sum(text.get_window_extent(renderer).overlaps(box) for text in axis.texts)
    return int(count)

def boxed_legend(axis,handles,columns=None):
    columns=columns or int(np.ceil(len(handles)/2));anchor=legend_anchor(axis)
    for step in range(40):
        leg=axis.legend(handles=handles,loc='upper left',bbox_to_anchor=anchor,ncol=columns,
            fontsize=LEGEND_FONT_SIZE,frameon=True,fancybox=False,facecolor='white',framealpha=1.,
            edgecolor='#6D7380',borderpad=.30,labelspacing=.30,handlelength=1.6,
            handletextpad=.35,columnspacing=.70,borderaxespad=.35,labelcolor='linecolor')
        leg.get_frame().set_linewidth(.65)
        axis.figure.canvas.draw();renderer=axis.figure.canvas.get_renderer();box=leg.get_window_extent(renderer)
        if box.x1>axis.get_window_extent(renderer).x1+1e-6:raise ValueError('Widen this panel to fit the common two-row legend')
        # Keep a 2 pt clearance below/right for backend-specific text metrics.
        clearance=2*axis.figure.dpi/72
        data_box=Bbox.from_extents(box.x0,box.y0-clearance,box.x1+clearance,box.y1)
        if covered_artists(axis,data_box,renderer)==0:
            leg._anchor=anchor;leg._columns=columns;leg._headroom_steps=step
            return leg
        leg.remove();low,high=axis.get_ylim()
        high=np.exp(np.log(low)+(np.log(high)-np.log(low))*1.12) if axis.get_yscale()=='log' else low+(high-low)*1.12
        axis.set_ylim(low,high)
    raise ValueError('Common legend position would cover the data')

def place_captions(fig,axes,titles,columns):
    fig.canvas.draw();renderer=fig.canvas.get_renderer();captions=[]
    for first in range(0,len(axes),columns):
        row=axes[first:first+columns]
        bottom=min(text.get_window_extent(renderer).y0 for axis in row for text in [axis.xaxis.label,*axis.get_xticklabels()] if text.get_text().strip())
        y=bottom/fig.bbox.height-5/(72*fig.get_figheight())
        for offset,axis in enumerate(row):
            box=axis.get_position()
            captions.append(fig.text((box.x0+box.x1)/2,y,titles[first+offset],ha='center',va='top',fontsize=PANEL_TITLE_SIZE))
    return captions

def panel_metrics(axis,caption=None):
    fig=axis.figure;fig.canvas.draw();renderer=fig.canvas.get_renderer();box=axis.get_position();leg=axis.get_legend()
    axis_box=np.array([box.x0,box.y0,box.x1,box.y1])*np.array([fig.get_figwidth()*72,fig.get_figheight()*72]*2)
    legend_box=leg.get_window_extent(renderer).extents*72/fig.dpi
    assert axis_box[0]<=legend_box[0]<legend_box[2]<=axis_box[2] and axis_box[1]<=legend_box[1]<legend_box[3]<=axis_box[3]
    return {'xlabel':axis.get_xlabel(),'ylabel':axis.get_ylabel(),'axis_label_fontsize_pt':axis.yaxis.label.get_fontsize(),
        'x_axis_label_fontsize_pt':axis.xaxis.label.get_fontsize(),'tick_label_fontsize_pt':TICK_LABEL_SIZE,'legend_fontsize_pt':LEGEND_FONT_SIZE,
        'axes_bbox_from_bottom_pt':axis_box.tolist(),'legend_bbox_from_bottom_pt':legend_box.tolist(),
        'legend_anchor':list(legend_anchor(axis)),'legend_left_margin_pt':float(legend_box[0]-axis_box[0]),
        'legend_top_margin_pt':float(axis_box[3]-legend_box[3]),'legend_position':'upper left','legend_boxed':True,
        'legend_rows':2,'legend_columns':leg._columns,'legend_labels':[t.get_text() for t in leg.get_texts()],
        'covered_data_artists':covered_artists(axis,leg.get_window_extent(renderer),renderer),
        'title':caption.get_text() if caption is not None else None,
        'caption_fontsize_pt':caption.get_fontsize() if caption is not None else None,
        'caption_bbox_from_bottom_pt':(caption.get_window_extent(renderer).extents*72/fig.dpi).tolist() if caption is not None else None,
        'y_scale':axis.get_yscale(),'display_y_limits':list(axis.get_ylim())}
