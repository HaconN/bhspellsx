"""Deterministic finished wave textures + reference composites. Python 3, numpy, Pillow.
Run from any directory. RGBA straight alpha; only textures/ goes into the mod jar.
The previews use the same 72-segment blade and UV layout as the Java mesh (not screenshots).
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
BASE = Path(__file__).resolve().parents[1]
OUT = BASE / 'src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan'
PREVIEW = BASE / 'tools/previews/jing_guang_pan'
PALETTE = {'gold':'D8AE48','deep_gold':'98702B','cream':'FFF3CF','white':'FFFBEF','teal':'83BDB3'}
RADIUS, ARC, THICKNESS, SEGMENTS = 1., 150., .22, 72
DEPTH, TAPER_POWER = .24, .7

def rgb(name): return np.array([int(PALETTE[name][i:i+2],16)/255 for i in (0,2,4)])
def smooth(x):
    x=np.clip(x,0,1); return x*x*(3-2*x)
def strip(w,h,kind):
    u,v=np.meshgrid(np.linspace(0,1,w),np.linspace(0,1,h))
    alpha=np.zeros((h,w)); color=np.zeros((h,w,3))
    def layer(a,c):
        nonlocal alpha,color
        a=np.clip(a,0,1); color=color*(1-a[...,None])+c*a[...,None]; alpha=alpha*(1-a)+a
    # V=1 is the convex leading edge: a sharp warm-white ridge, trailing gold wake.
    edge=smooth(u/.12)*smooth((1-u)/.12) if kind=='core' else np.ones_like(u)
    ridge=.88+.006*np.sin(2*np.pi*u*3)
    behind=np.maximum(0,ridge-v)
    if kind=='core':
        body=np.exp(-behind/.24)*smooth((ridge+.035-v)/.025)*smooth(v/.15)
        layer(body*.9*edge,rgb('deep_gold'))
        layer(body*.9*edge,rgb('gold'))
        layer(np.exp(-((v-ridge)/.024)**4)*edge*.98,rgb('white'))
        layer(np.exp(-((v-(ridge-.045))/.027)**2)*edge*.8,rgb('cream'))
    # Three broad, coherent currents instead of many subpixel hairs.
    for i,offset in enumerate([.30,.51,.70]):
        curve=offset+.03*np.sin(2*np.pi*(u*(2+i)+i*.21))
        width=.018 if kind=='core' else .023
        gate=.45+.55*np.sin(np.pi*(u*2+i*.31))**2
        layer(np.exp(-((v-curve)/width)**4)*gate*edge*(.65 if kind=='core' else .85),rgb('teal' if i==0 else 'gold'))
    # Two broad, tapered offshoots stay on the trailing side.
    for i in range(2):
        phase=(u+i*.47)%1
        curve=.62-.34*phase
        layer(np.exp(-((v-curve)/.016)**2)*np.sin(np.pi*phase)**4*edge*.45,rgb('gold'))
    if kind=='flow':
        # Enforce wrap continuity; the periodic filaments carry the scrolling motion.
        color[:,-1]=color[:,0]; alpha[:,-1]=alpha[:,0]
    straight=np.divide(color, np.maximum(alpha[...,None],1e-7))
    margin=smooth(np.minimum(v,1-v)*h/8)
    alpha*=margin;alpha[[0,-1],:]=0
    if kind=='core':alpha[:,[0,-1]]=0
    # Unpremultiply: transparent texels retain warm RGB to avoid black filtering fringes.
    straight=np.where((alpha>1e-5)[...,None],straight,rgb('gold'))
    return Image.fromarray(np.uint8(np.clip(np.dstack([straight,alpha]),0,1)*255),'RGBA')

def glow():
    u,v=np.meshgrid(np.linspace(0,1,256),np.linspace(0,1,64))
    a=np.exp(-((v-.65)/.18)**2)*smooth(np.minimum(v,1-v)*64/8)*.36
    a[[0,-1],:]=0
    c=np.broadcast_to(rgb('gold'),(64,256,3))
    return Image.fromarray(np.uint8(np.dstack([c,a])*255),'RGBA')
def spark():
    y,x=np.mgrid[-1:1:64j,-1:1:64j]
    star=np.exp(-np.abs(x)*32-np.abs(y)*3.8)+np.exp(-np.abs(y)*32-np.abs(x)*3.8)
    halo=.3*np.exp(-(x*x+y*y)/.10)
    a=np.clip(star+halo,0,1)*smooth((.75-np.maximum(np.abs(x),np.abs(y)))/.15)
    t=np.clip(star,0,1)[...,None]
    c=rgb('gold')*(1-t)+rgb('white')*t
    return Image.fromarray(np.uint8(np.dstack([c,a])*255),'RGBA')

def font(n):
    try:return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)
    except OSError:return ImageFont.load_default()
def mesh_composite(textures,bg,roll,size=(840,520),view_name="OBLIQUE"):
    w,h=size; canvas=np.broadcast_to(np.array(bg)/255,(h,w,3)).copy()
    scale=285.;cx=w/2;cy=h*.52
    camera=np.array({'OBLIQUE':(0,3,-4),'SHOULDER':(.65,.32,-3),'SIDE':(3,0,0)}[view_name],float)
    look=-camera/np.linalg.norm(camera)
    right=np.array([look[2],0,-look[0]]);right/=np.linalg.norm(right)
    vertical=np.cross(look,right)
    roll_r=math.radians(roll)
    across=np.array([math.cos(roll_r),math.sin(roll_r),0])
    normal=np.array([-math.sin(roll_r),math.cos(roll_r),0])
    def world_point(i,side,width,depth=0):
        t=i/SEGMENTS;a=math.radians((t-.5)*ARC)
        taper=max(0,math.sin(math.pi*t))**TAPER_POWER
        r=RADIUS+side*THICKNESS/2*width*taper
        return across*(math.sin(a)*r)+np.array([0,0,math.cos(a)*r-RADIUS])+normal*depth
    def project(world):return np.array([cx+world.dot(right)*scale,cy-world.dot(vertical)*scale])
    def point(i,side,width,depth=0):return project(world_point(i,side,width,depth))
    def spark_point(i,side,width):
        t=i/SEGMENTS;a=math.radians((t-.5)*ARC)
        r=RADIUS+side*.22/2*width*max(0,math.sin(math.pi*t))**.7
        return project(across*(math.sin(a)*r)+np.array([0,0,math.cos(a)*r-RADIUS]))
    def fade(t):return smooth(min(t,1-t)/.14)
    def triangle(points,uv,texture,add,opacity):
        nonlocal canvas
        lo=np.maximum(0,np.floor(np.min(points,axis=0)).astype(int));hi=np.minimum([w-1,h-1],np.ceil(np.max(points,axis=0)).astype(int))
        if np.any(hi<lo):return
        x,y=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
        p0,p1,p2=points; ab=p1-p0; ac=p2-p0; det=ab[0]*ac[1]-ab[1]*ac[0]
        if abs(det)<1e-9:return
        b=((x-p0[0])*(p2[1]-p0[1])-(y-p0[1])*(p2[0]-p0[0]))/det
        c=((p1[0]-p0[0])*(y-p0[1])-(p1[1]-p0[1])*(x-p0[0]))/det
        mask=(b>=0)&(c>=0)&(b+c<1)
        texuv=uv[0]+b[...,None]*(uv[1]-uv[0])+c[...,None]*(uv[2]-uv[0])
        th,tw=texture.shape[:2];tx=np.clip((texuv[...,0]*(tw-1)).astype(int),0,tw-1);ty=np.clip((texuv[...,1]*(th-1)).astype(int),0,th-1)
        fx=np.clip(texuv[...,0]*(tw-1),0,tw-1);fy=np.clip(texuv[...,1]*(th-1),0,th-1)
        x0=np.floor(fx).astype(int);y0=np.floor(fy).astype(int);x1=np.minimum(x0+1,tw-1);y1=np.minimum(y0+1,th-1)
        wx=(fx-x0)[...,None];wy=(fy-y0)[...,None]
        sample=((texture[y0,x0]*(1-wx)+texture[y0,x1]*wx)*(1-wy)+(texture[y1,x0]*(1-wx)+texture[y1,x1]*wx)*wy)/255
        op=opacity[0]+b*(opacity[1]-opacity[0])+c*(opacity[2]-opacity[0])
        a=sample[...,3:4]*mask[...,None]*op[...,None]
        view=canvas[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
        view[:]=np.clip(view+sample[...,:3]*a if add else view*(1-a)+sample[...,:3]*a,0,1)
    def draw_quad(pts,uv,tex,add,alpha):
        for idx in [[0,1,2],[0,2,3]]:triangle(pts[idx],uv[idx],tex,add,np.array(alpha)[idx])
    for depth in [-.12,.12]:
        for i in range(SEGMENTS):
            pts=np.array([point(i,-1,1,depth),point(i+1,-1,1,depth),point(i+1,1,1,depth),point(i,1,1,depth)])
            uv=np.array([[i/SEGMENTS,0],[(i+1)/SEGMENTS,0],[(i+1)/SEGMENTS,1],[i/SEGMENTS,1]])
            draw_quad(pts,uv,np.array(textures['crescent_core']),False,[fade(i/SEGMENTS)*.82,fade((i+1)/SEGMENTS)*.82,fade((i+1)/SEGMENTS)*.82,fade(i/SEGMENTS)*.82])
    for side in [-1,1]:
        for i in range(SEGMENTS):
            pts=np.array([point(i,side,1,-.12),point(i+1,side,1,-.12),point(i+1,side,1,.12),point(i,side,1,.12)])
            uv=np.array([[i/SEGMENTS,0],[(i+1)/SEGMENTS,0],[(i+1)/SEGMENTS,1],[i/SEGMENTS,1]])
            draw_quad(pts,uv,np.array(textures['crescent_core']),False,[fade(i/SEGMENTS)*.492,fade((i+1)/SEGMENTS)*.492,fade((i+1)/SEGMENTS)*.492,fade(i/SEGMENTS)*.492])
    for key,width,opacity in [('soft_glow',1.8,.14),('energy_flow',1.25,.5)]:
        for i in range(SEGMENTS):
            pts=np.array([point(i,-1,width),point(i+1,-1,width),point(i+1,1,width),point(i,1,width)])
            uv=np.array([[i/SEGMENTS,0],[(i+1)/SEGMENTS,0],[(i+1)/SEGMENTS,1],[i/SEGMENTS,1]])
            draw_quad(pts,uv,np.array(textures[key]),True,[fade(i/SEGMENTS)*opacity,fade((i+1)/SEGMENTS)*opacity,fade((i+1)/SEGMENTS)*opacity,fade(i/SEGMENTS)*opacity])
    # Camera-facing leading ribbon, using the existing cream/gold part of core.
    def edge_offset(i):
        t=i/SEGMENTS
        tangent=world_point(min(SEGMENTS,i+.072),1,1)-world_point(max(0,i-.072),1,1)
        side=np.cross(tangent,-look)
        if np.linalg.norm(side)<1e-9:side=normal.copy()
        else:side/=np.linalg.norm(side)
        return side*.065*.5*max(0,math.sin(math.pi*t))**TAPER_POWER
    for i in range(SEGMENTS):
        a,b=world_point(i,1,1),world_point(i+1,1,1);x,y=edge_offset(i),edge_offset(i+1)
        pts=np.array([project(a-x),project(b-y),project(b+y),project(a+x)])
        uv=np.array([[i/SEGMENTS,.6],[(i+1)/SEGMENTS,.6],[(i+1)/SEGMENTS,1],[i/SEGMENTS,1]])
        draw_quad(pts,uv,np.array(textures['crescent_core']),True,[.9*fade(i/SEGMENTS),.9*fade((i+1)/SEGMENTS),.9*fade((i+1)/SEGMENTS),.9*fade(i/SEGMENTS)])
    tail_side=np.cross(np.array([0.,0.,1.]),-look)
    if np.linalg.norm(tail_side)<1e-9:tail_side=normal.copy()
    else:tail_side/=np.linalg.norm(tail_side)
    tail_side*=.045/2
    for i in range(3):
        head=world_point((i+1)/4*SEGMENTS,0,1);tail=head-np.array([0,0,.9])
        pts=np.array([project(head-tail_side),project(tail-tail_side),project(tail+tail_side),project(head+tail_side)])
        draw_quad(pts,np.array([[0,0],[1,0],[1,1],[0,1]]),np.array(textures['soft_glow']),True,[.38,0,0,.38])
    image=Image.fromarray(np.uint8(canvas*255)).convert('RGBA')
    rng=np.random.default_rng(83)
    for i in range(8):
        q=spark_point(int(rng.integers(1,12))*6,float(rng.choice([-1,1])),float(rng.uniform(2,5)))
        n=int(rng.integers(26,43));sp=textures['spark'].resize((n,n),Image.Resampling.LANCZOS)
        image.alpha_composite(sp,(int(q[0]-n/2),int(q[1]-n/2)))
    d=ImageDraw.Draw(image);d.text((24,20),('RIGHT  +22' if roll>0 else 'LEFT  -22')+'  /  '+view_name+'  /  '+('DAY' if sum(bg)>500 else 'NIGHT'),font=font(23),fill=('#4A4435' if sum(bg)>500 else '#F7E9C5'))
    return image.convert('RGB')

def main():
    OUT.mkdir(parents=True,exist_ok=True);PREVIEW.mkdir(parents=True,exist_ok=True)
    import sys
    if '--preview-only' in sys.argv:
        textures={name:Image.open(OUT/(name+'.png')).convert('RGBA') for name in ['crescent_core','energy_flow','soft_glow','spark']}
    else:
        textures={'crescent_core':strip(512,128,'core'),'energy_flow':strip(512,128,'flow'),'soft_glow':glow(),'spark':spark()}
        for name,im in textures.items():im.save(OUT/(name+'.png'))
    sheet=Image.new('RGB',(1440,1100),'#232A31');d=ImageDraw.Draw(sheet)
    d.text((28,16),'JING GUANG PAN / production texture atlas preview',font=font(27),fill='#FFF3CF')
    for row,(name,im) in enumerate(textures.items()):
        y=70+row*252;d.text((28,y),f'{name}   {im.width} x {im.height}   / straight RGBA',font=font(19),fill='#FFF3CF')
        for col,bg in enumerate([(13,22,32),(224,234,239)]):
            panel=Image.new('RGBA',(680,196),bg+(255,));thumb=im.copy();thumb.thumbnail((640,166),Image.Resampling.LANCZOS)
            if name=='spark':thumb=im.resize((128,128),Image.Resampling.NEAREST)
            panel.alpha_composite(thumb,((680-thumb.width)//2,(196-thumb.height)//2));sheet.paste(panel.convert('RGB'),(24+col*712,y+34))
    sheet.save(PREVIEW/'textures_dark_light.png')
    assembled=Image.new('RGB',(1680,1040))
    for row,bg in enumerate([(13,22,32),(224,234,239)]):
        for col,roll in enumerate([22,-22]):assembled.paste(mesh_composite(textures,bg,roll),(840*col,520*row))
    assembled.save(PREVIEW/'waves_right_left.png')
    mesh_composite(textures,(13,22,32),22,view_name='SHOULDER').save(PREVIEW/'wave_shoulder.png')
    mesh_composite(textures,(13,22,32),22,view_name='SIDE').save(PREVIEW/'wave_side.png')
    # Verify deliverable constraints, including exactly matching wrap-edge texels.
    for name,im in textures.items():
        a=np.array(im);assert im.mode=='RGBA' and np.all(a[0,:,3]==0) and np.all(a[-1,:,3]==0)
        if name in ('energy_flow','soft_glow'):assert np.array_equal(a[:,0],a[:,-1])
    print('Generated four production textures and four unpackaged previews:',PREVIEW)
if __name__=='__main__':main()
