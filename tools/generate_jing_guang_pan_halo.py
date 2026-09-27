"""Deterministic halo textures and software 3D review renders. Python + numpy + Pillow.
Run from any directory. Reads Java tuning constants; writes resource textures and unpackaged previews.
Textures are straight-alpha RGBA. Preview is a design simulation, not an in-game capture.
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
BASE=Path(__file__).resolve().parent
OUT=BASE.parent/'src/main/resources/assets/bhspellsx/textures/vfx/jing_guang_pan'
PRE=BASE/'previews/jing_guang_pan_halo'
GOLD=np.array([216,174,72])/255
DEEP=np.array([152,112,43])/255
CREAM=np.array([255,243,207])/255
WHITE=np.array([255,251,239])/255
DIAMETER=1.65
CENTER_Y=1.75
BACK_Z=.425 # player back surface .125; clearance .30
RAY_RADIUS=1.23
RIM_TUBE_RADIUS=.018
RING_ALPHA=.95
INNER_ALPHA=.88
INNER_ADDITIVE=.28
INNER_BACK_MULTIPLIER=.10
BACK_FADE_START_DOT=0.0
BACK_FADE_FULL_DOT=.85
RING_CORE_WIDTH=.032
RING_SOFT_WIDTH=.105
RAY_COUNT=144
RAYS_ALPHA=.60
GLOW_ALPHA=.24
SPARK_SIZE=.045
SEED=427

# Read the Java tuning source without executing or compiling it.
import re
CONSTANTS=BASE.parent/'src/main/java/net/offkung/bhspellsx/client/renderer/jing_guang_pan/JingGuangPanHaloConstants.java'
values=CONSTANTS.read_text(encoding='utf-8-sig')
def setting(name):
 match=re.search(r'\b'+name+r'\s*=\s*([0-9.]+)[fFdD]?(?=[,;])',values)
 if not match: raise ValueError('Missing numeric constant: '+name)
 return float(match.group(1))
for name in ['DIAMETER','CENTER_Y','RAY_RADIUS','RIM_TUBE_RADIUS','RING_ALPHA','INNER_ALPHA',
             'INNER_ADDITIVE','BACK_FADE_START_DOT','BACK_FADE_FULL_DOT','RING_CORE_WIDTH',
             'RING_SOFT_WIDTH','RAYS_ALPHA','GLOW_ALPHA','SPARK_SIZE']:
 globals()[name]=setting(name)
INNER_BACK_MULTIPLIER=setting('BACK_INNER_MIN')
BACK_Z=setting('BODY_HALF_DEPTH')+setting('BACK_GAP')
RAY_COUNT=int(setting('RAY_COUNT'))
SPARK_COUNT=int(setting('SPARK_COUNT'))
SEED=int(setting('SPARK_SEED'))

def smooth(x):
 x=np.clip(x,0,1); return x*x*(3-2*x)
def rgba(c,a):
 c=np.broadcast_to(c,(*a.shape,3)); a=np.clip(a,0,1).copy()
 a[[0,-1],:]=0; a[:,[0,-1]]=0
 return Image.fromarray(np.uint8(np.clip(np.dstack((c,a)),0,1)*255),'RGBA')
def texture_set():
 textures={}
 for kind,size in [('halo_ring',512),('halo_inner',256),('halo_rays',512),('halo_spark',64)]:
  y,x=np.mgrid[-1:1:complex(size),-1:1:complex(size)]; r=np.hypot(x,y); t=np.arctan2(y,x)
  edge=smooth((1-r)/.09)
  ring=DIAMETER/2/RAY_RADIUS
  if kind=='halo_ring':
   ridge=np.exp(-((r-ring)/RING_CORE_WIDTH)**2)
   shoulder=np.exp(-((r-ring)/.060)**2)
   halo=np.exp(-((r-ring)/RING_SOFT_WIDTH)**2)*.62
   a=np.maximum(shoulder*.98,halo)*edge
   mix=ridge*.86
   c=GOLD*(1-mix[...,None])+WHITE*mix[...,None]
   # Broad pigmented gold outer shoulder remains visible against a bright sky.
   outer=smooth((np.abs(r-ring)-.035)/.075)
   c=c*(1-.32*outer[...,None])+DEEP*.32*outer[...,None]
  elif kind=='halo_inner':
   # A filled warm sun: maximum luminance behind the head, no grey spiral pattern.
   a=.98*np.exp(-(r/(ring*.98))**4)*smooth((ring+.14-r)/.14)*edge
   hot=np.exp(-(r/.31)**2)
   c=GOLD*(1-hot[...,None])+WHITE*hot[...,None]
  elif kind=='halo_rays':
   energy=np.zeros_like(r); rng=np.random.default_rng(SEED)
   for i in range(RAY_COUNT):
    angle=2*np.pi*(i+rng.uniform(-.42,.42))/RAY_COUNT
    dt=np.arctan2(np.sin(t-angle),np.cos(t-angle))
    width=rng.uniform(.009,.027); length=rng.uniform(.085,.29)
    outward=np.exp(-(np.maximum(0,r-ring)/length)**2)
    inward=smooth((r-(ring-.24))/.24)
    ray=np.exp(-(dt/width)**2)*outward*inward*rng.uniform(.32,.80)
    energy+=ray
   a=(1-np.exp(-energy))*edge
   tint=np.exp(-((r-ring)/.095)**2)*.65
   c=GOLD*(1-tint[...,None])+CREAM*tint[...,None]
  else:
   a=np.maximum(np.exp(-np.abs(x)*42-np.abs(y)*5),np.exp(-np.abs(y)*42-np.abs(x)*5))
   a=np.maximum(a,np.exp(-(r/.10)**2)); a*=smooth((1-r)/.20)
   tint=np.exp(-(r/.25)**2); c=GOLD*(1-tint[...,None])+WHITE*tint[...,None]
  textures[kind]=rgba(c,a)
 return textures

def font(n):
 return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)

# Orthographic preview with depth-buffered Minecraft-sized cuboids and a physical thin rim.
W,H=560,690
SCALE=178 # framing only: keep raised halo/rays clear of the panel title

def scene(tex,view,night):
 angle={'front':0,'back':180,'side':90}[view]*math.pi/180
 right=np.array([math.cos(angle),0,math.sin(angle)])
 toward=np.array([math.sin(angle),0,-math.cos(angle)])
 def proj(v):
  v=np.asarray(v); return np.stack([W/2+v@right*SCALE,H-78-v[...,1]*SCALE,v@toward],axis=-1)
 top=np.array([12,22,40] if night else [130,182,224])/255
 bottom=np.array([29,43,56] if night else [220,235,239])/255
 k=np.linspace(0,1,H)[:,None,None]
 img=np.broadcast_to(top*(1-k)+bottom*k,(H,W,3)).copy()
 depth=np.full((H,W),-1e9)
 def tri(points,color):
  pts=proj(points); x0=max(0,int(pts[:,0].min()));x1=min(W,int(pts[:,0].max())+1)
  y0=max(0,int(pts[:,1].min()));y1=min(H,int(pts[:,1].max())+1)
  if x0>=x1 or y0>=y1:return
  x,y=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5)
  a,b,c=pts; den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(den)<1e-8:return
  u=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/den
  v=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/den
  z=u*a[2]+v*b[2]+(1-u-v)*c[2]
  mask=(u>=0)&(v>=0)&(u+v<=1)&(z>depth[y0:y1,x0:x1]+1e-7)
  img[y0:y1,x0:x1][mask]=color;depth[y0:y1,x0:x1][mask]=z[mask]
 def box(lo,hi,color):
  vertices=np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])
  for face,shade in [([0,1,3,2],.72),([4,6,7,5],.90),([0,4,5,1],.6),([2,3,7,6],1),([0,2,6,4],.86),([1,5,7,3],.80)]:
   a,b,c,d=vertices[face]; col=np.array(color)/255*shade*(.8 if night else 1)
   tri([a,b,c],col);tri([a,c,d],col)
 box([-.25,.75,-.125],[.25,1.45,.125],[53,102,112])
 box([-.25,1.45,-.25],[.25,1.95,.25],[206,170,134])
 box([-.25,1.85,-.25],[.25,1.97,.25],[60,49,46])
 for sign in [-1,1]:
  cx=sign*.38;box([cx-.12,.77,-.125],[cx+.12,1.43,.125],[64,118,124])
  box([cx-.12,.65,-.125],[cx+.12,.85,.125],[206,170,134])
  cx=sign*.13;box([cx-.12,.08,-.125],[cx+.12,.75,.125],[58,67,89])
  box([cx-.12,0,-.18],[cx+.12,.15,.13],[48,42,45])
 for cx in [-.12,.12]:box([cx-.035,1.65,-.257],[cx+.035,1.70,-.25],[43,43,42])
 bodydepth=depth.copy()
 # Full-plane straight alpha textures: back is really behind, not pasted over the front silhouette.
 def plane(im,gain,add=False,angle2=0):
  if abs(math.cos(angle))<1e-6:return
  xx,yy=np.meshgrid(np.arange(W)+.5,np.arange(H)+.5)
  wx=((xx-W/2)/SCALE-BACK_Z*math.sin(angle))/math.cos(angle)
  wy=(H-78-yy)/SCALE-CENTER_Y
  ux=(wx*math.cos(angle2)+wy*math.sin(angle2))/RAY_RADIUS
  uy=(-wx*math.sin(angle2)+wy*math.cos(angle2))/RAY_RADIUS
  ix=(ux+1)*.5*(im.width-1); iy=(1-uy)*.5*(im.height-1)
  inside=(ix>=0)&(ix<im.width-1)&(iy>=0)&(iy<im.height-1)
  x=np.clip(ix,0,im.width-1).astype(int);y=np.clip(iy,0,im.height-1).astype(int)
  pix=np.asarray(im)/255; sample=pix[y,x]
  z=wx*math.sin(angle)-BACK_Z*math.cos(angle)
  alpha=sample[:,:,3]*gain*inside*(z>=bodydepth-1e-5)
  if add:img[:]=np.clip(img+sample[:,:,:3]*alpha[...,None],0,1)
  else:img[:]=img*(1-alpha[...,None])+sample[:,:,:3]*alpha[...,None]
 # Dot(camera direction, body backward): +1 rear, 0 side, -1 front.
 rear_dot=-math.cos(angle)
 rear_weight=float(smooth((rear_dot-BACK_FADE_START_DOT)/(BACK_FADE_FULL_DOT-BACK_FADE_START_DOT)))
 inner_view_gain=1-(1-INNER_BACK_MULTIPLIER)*rear_weight
 plane(tex['halo_inner'],INNER_ALPHA*inner_view_gain*(1-rear_weight))
 plane(tex['halo_inner'],INNER_ADDITIVE*inner_view_gain,True)
 plane(tex['halo_rays'],RAYS_ALPHA,True)
 plane(tex['halo_ring'],RING_ALPHA)
 plane(tex['halo_ring'],GLOW_ALPHA,True)
 # Physical thin tube: keeps the actual side view readable without billboard-facing the whole halo.
 # Draw its opaque core after plane layers, using the same depth test as the player.
 rad=DIAMETER/2
 for i in range(160):
  a=2*math.pi*i/160;b=2*math.pi*(i+1)/160
  for j in range(8):
   pts=[]
   for u,v in [(a,j),(b,j),(b,j+1),(a,j+1)]:
    q=v*2*math.pi/8;r=rad+RIM_TUBE_RADIUS*math.cos(q)
    pts.append([r*math.cos(u),CENTER_Y+r*math.sin(u),BACK_Z+RIM_TUBE_RADIUS*math.sin(q)])
   mix=.55+.40*abs(math.sin((j+.5)*2*math.pi/8))
   col=GOLD*(1-mix)+CREAM*mix
   tri(pts[:3],col);tri([pts[0],pts[2],pts[3]],col)
 # A few small billboard sparks, fixed deterministic phase of the proposed orbit.
 rng=np.random.default_rng(SEED)
 for i in range(SPARK_COUNT):
  a=rng.uniform(0,2*math.pi);r=rng.uniform(.87,1.08)
  pos=np.array([r*math.cos(a),CENTER_Y+r*math.sin(a),BACK_Z+rng.uniform(-.035,.035)])
  sx,sy,z=proj(pos); n=round(SPARK_SIZE*SCALE*rng.uniform(.7,1.4));n=max(5,n)
  spark=np.asarray(tex['halo_spark'].resize((n,n),Image.Resampling.LANCZOS))/255
  x0=int(sx-n/2);y0=int(sy-n/2)
  if x0<0 or y0<0 or x0+n>W or y0+n>H:continue
  alpha=spark[:,:,3]*.9*(z>=bodydepth[y0:y0+n,x0:x0+n])
  img[y0:y0+n,x0:x0+n]=np.clip(img[y0:y0+n,x0:x0+n]+spark[:,:,:3]*alpha[...,None],0,1)
 im=Image.fromarray(np.uint8(np.clip(img,0,1)*255));d=ImageDraw.Draw(im)
 ink='#FFF3CF' if night else '#433919'
 d.text((22,18),f'{view.upper()} / {"NIGHT" if night else "DAY"}',fill=ink,font=font(23))
 d.text((22,50),f'{DIAMETER:.2f} m rim | center {CENTER_Y:.2f} m | back gap 0.30 m',fill=ink,font=font(15))
 d.text((22,73),f'Inner view strength: {inner_view_gain:.0%}',fill=ink,font=font(14))
 d.text((22,H-42),'Design simulation - not a Minecraft screenshot',fill=ink,font=font(15))
 return im

def main():
 OUT.mkdir(parents=True,exist_ok=True);PRE.mkdir(parents=True,exist_ok=True)
 textures=texture_set()
 for name,im in textures.items():
  im.save(OUT/(name+'.png'))
  a=np.asarray(im);assert im.mode=='RGBA' and not a[[0,-1],:,3].any() and not a[:,[0,-1],3].any()
 sheet=Image.new('RGB',(1680,1380))
 for row,night in enumerate([False,True]):
  for col,view in enumerate(['back','front','side']):
   im=scene(textures,view,night);im.save(PRE/f'halo_{view}_{"night" if night else "day"}.png');sheet.paste(im,(col*W,row*H))
 sheet.save(PRE/'halo_views_day_night.png')
 atlas=Image.new('RGB',(1080,1260),'#17212D');d=ImageDraw.Draw(atlas)
 d.text((24,15),'HALO / straight-alpha production textures',fill='#FFF3CF',font=font(25))
 for i,(name,im) in enumerate(textures.items()):
  y=65+i*295;d.text((24,y),f'{name} - {im.width} x {im.height}',fill='#FFF3CF',font=font(19))
  for j,bg in enumerate(['#101C2C','#DDEAF1']):
   panel=Image.new('RGBA',(510,250),bg);thumb=im.resize((238,238),Image.Resampling.LANCZOS)
   if name=='halo_spark':thumb=im.resize((80,80),Image.Resampling.NEAREST)
   panel.alpha_composite(thumb,((510-thumb.width)//2,(250-thumb.height)//2));atlas.paste(panel.convert('RGB'),(24+j*528,y+30))
 atlas.save(PRE/'textures_dark_light.png')
 print('Textures:',OUT);print('Preview:',PRE/'halo_views_day_night.png')
if __name__=='__main__':main()
