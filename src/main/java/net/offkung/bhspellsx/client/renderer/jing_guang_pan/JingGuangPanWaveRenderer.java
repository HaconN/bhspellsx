package net.offkung.bhspellsx.client.renderer.jing_guang_pan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collection;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import static net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanConstants.*;
import static net.offkung.bhspellsx.client.renderer.jing_guang_pan.JingGuangPanVfxConstants.*;

public final class JingGuangPanWaveRenderer {
    public static void render(RenderLevelStageEvent event,Collection<JingGuangPanVfx.Visual> waves) {
        var mc=Minecraft.getInstance();
        var buffers=mc.renderBuffers().bufferSource();
        var stack=event.getPoseStack();
        Vec3 camera=event.getCamera().getPosition();
        float partial=event.getPartialTick();
        // Pigmented base first, luminous detail second. All waves share the same four batches.
        for (boolean additive : new boolean[]{false,true}) {
            for (var wave : waves) {
                double distance=wave.previous+(wave.distance-wave.previous)*partial;
                Vec3 position=wave.source.origin().add(wave.source.direction().scale(distance));
                if (position.distanceToSqr(camera)>RENDER_DISTANCE*RENDER_DISTANCE) continue;
                float alpha=1-(wave.previousFade+(wave.fade-wave.previousFade)*partial)/FADE_TICKS;
                if (alpha<=0) continue;
                stack.pushPose(); stack.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
                float age=wave.age+partial;
                if (!additive) {
                    var v=buffers.getBuffer(JingGuangPanRenderTypes.get("crescent_core",false));
                    strip(stack,v,wave,1,-WAVE_DEPTH/2,CORE_ALPHA*alpha,0);
                    strip(stack,v,wave,1,WAVE_DEPTH/2,CORE_ALPHA*alpha,0);
                    sides(stack,v,wave,CORE_ALPHA*alpha);
                } else {
                    strip(stack,buffers.getBuffer(JingGuangPanRenderTypes.get("soft_glow",true)),wave,GLOW_WIDTH,0,GLOW_ALPHA*alpha,0);
                    strip(stack,buffers.getBuffer(JingGuangPanRenderTypes.get("energy_flow",true)),wave,FLOW_WIDTH,0,FLOW_ALPHA*alpha,age*FLOW_U_PER_TICK);
                    edgeRibbon(stack,buffers.getBuffer(JingGuangPanRenderTypes.get("crescent_core",true)),wave,camera.subtract(position),alpha);
                    tails(stack,buffers.getBuffer(JingGuangPanRenderTypes.get("soft_glow",true)),wave,camera.subtract(position),distance,alpha);
                    sparks(stack,buffers.getBuffer(JingGuangPanRenderTypes.get("spark",true)),wave,age,alpha,event);
                }
                stack.popPose();
            }
            if (!additive) buffers.endBatch(JingGuangPanRenderTypes.get("crescent_core",false));
            else for (String tex : new String[]{"soft_glow","energy_flow","spark","crescent_core"}) buffers.endBatch(JingGuangPanRenderTypes.get(tex,true));
        }
    }
    private static float tipAlpha(double t) {
        double u=Math.min(1,Math.min(t,1-t)/TIP_FADE_FRACTION);
        return (float)(u*u*(3-2*u));
    }
    private static void strip(PoseStack p,VertexConsumer v,JingGuangPanVfx.Visual w,double width,double depth,float alpha,float scroll) {
        for (int i=0;i<RENDER_SEGMENTS;i++) {
            double t=(double)i/RENDER_SEGMENTS, end=(double)(i+1)/RENDER_SEGMENTS;
            quad(p,v,w.shape.pointAt(t,-1,width,depth),w.shape.pointAt(end,-1,width,depth),
                    w.shape.pointAt(end,1,width,depth),w.shape.pointAt(t,1,width,depth),
                    (float)t+scroll,(float)end+scroll,alpha*tipAlpha(t),alpha*tipAlpha(end));
        }
    }
    private static void sides(PoseStack p,VertexConsumer v,JingGuangPanVfx.Visual w,float alpha) {
        for (int side : new int[]{-1,1}) for (int i=0;i<RENDER_SEGMENTS;i++) {
            double t=(double)i/RENDER_SEGMENTS, end=(double)(i+1)/RENDER_SEGMENTS;
            quad(p,v,w.shape.pointAt(t,side,1,-WAVE_DEPTH/2),w.shape.pointAt(end,side,1,-WAVE_DEPTH/2),
                w.shape.pointAt(end,side,1,WAVE_DEPTH/2),w.shape.pointAt(t,side,1,WAVE_DEPTH/2),
                (float)t,(float)end,alpha*.6F*tipAlpha(t),alpha*.6F*tipAlpha(end));
        }
    }
    private static Vec3 ribbonSide(Vec3 tangent,Vec3 eye,Vec3 fallback) {
        Vec3 side=tangent.cross(eye);
        return side.lengthSqr()>1.0E-12?side.normalize():fallback;
    }
    private static Vec3 edgeOffset(JingGuangPanVfx.Visual w,double t,Vec3 eye) {
        Vec3 at=w.shape.pointAt(t,1,1,0);
        Vec3 tangent=w.shape.pointAt(Math.min(1,t+.001),1,1,0).subtract(w.shape.pointAt(Math.max(0,t-.001),1,1,0));
        return ribbonSide(tangent,eye.subtract(at),w.shape.up)
            .scale(EDGE_WIDTH*.5*Math.pow(Math.max(0,Math.sin(Math.PI*t)),WAVE_TAPER_POWER));
    }
    private static void edgeRibbon(PoseStack p,VertexConsumer v,JingGuangPanVfx.Visual w,Vec3 eye,float alpha) {
        for(int i=0;i<RENDER_SEGMENTS;i++) {
            double t=(double)i/RENDER_SEGMENTS,end=(double)(i+1)/RENDER_SEGMENTS;
            Vec3 a=w.shape.pointAt(t,1,1,0),b=w.shape.pointAt(end,1,1,0);
            Vec3 x=edgeOffset(w,t,eye),y=edgeOffset(w,end,eye);
            float aa=alpha*EDGE_ALPHA*tipAlpha(t),ab=alpha*EDGE_ALPHA*tipAlpha(end);
            // Reuse just the approved gold-to-cream ridge; no new texture/color.
            vertex(p,v,a.subtract(x),(float)t,.60F,aa); vertex(p,v,b.subtract(y),(float)end,.60F,ab);
            vertex(p,v,b.add(y),(float)end,1,ab); vertex(p,v,a.add(x),(float)t,1,aa);
        }
    }
    private static void tails(PoseStack p,VertexConsumer v,JingGuangPanVfx.Visual w,Vec3 eye,double distance,float alpha) {
        double length=Math.min(TAIL_LENGTH,distance);
        if(length<=0)return;
        for(int i=0;i<TAIL_LINES;i++) {
            double t=(i+1.0)/(TAIL_LINES+1);
            Vec3 head=w.shape.pointAt(t,0,1,0),tail=head.subtract(w.shape.forward.scale(length));
            Vec3 side=ribbonSide(w.shape.forward,eye.subtract(head),w.shape.up).scale(TAIL_WIDTH/2);
            quad(p,v,head.subtract(side),tail.subtract(side),tail.add(side),head.add(side),0,1,alpha*TAIL_ALPHA,0);
        }
    }
    private static void sparks(PoseStack p,VertexConsumer v,JingGuangPanVfx.Visual w,float age,float alpha,RenderLevelStageEvent e) {
        Random random=new Random(w.source.id().getLeastSignificantBits());
        Vector3f x=new Vector3f(1,0,0).rotate(e.getCamera().rotation()), y=new Vector3f(0,1,0).rotate(e.getCamera().rotation());
        for (int i=0;i<SPARKS;i++) {
            int edge=1+random.nextInt(WAVE_SEGMENTS-1);
            Vec3 center=w.shape.point(edge,random.nextBoolean()?1:-1,2+random.nextDouble()*3,-age*.025);
            float size=SPARK_SIZE_MIN+random.nextFloat()*(SPARK_SIZE_MAX-SPARK_SIZE_MIN);
            Vec3 right=new Vec3(x.x,x.y,x.z).scale(size), up=new Vec3(y.x,y.y,y.z).scale(size);
            float blink=.8F+.2F*(float)Math.sin(age*1.6+i*1.7);
            quad(p,v,center.subtract(right).subtract(up),center.add(right).subtract(up),center.add(right).add(up),center.subtract(right).add(up),0,1,alpha*blink);
        }
    }
    private static void quad(PoseStack p,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u,float end,float alpha) {
        quad(p,v,a,b,c,d,u,end,alpha,alpha);
    }
    private static void quad(PoseStack p,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u,float end,float alpha,float endAlpha) {
        vertex(p,v,a,u,0,alpha); vertex(p,v,b,end,0,endAlpha); vertex(p,v,c,end,1,endAlpha); vertex(p,v,d,u,1,alpha);
    }
    private static void vertex(PoseStack p,VertexConsumer v,Vec3 a,float u,float y,float alpha) {
        v.vertex(p.last().pose(),(float)a.x,(float)a.y,(float)a.z).color(1F,1F,1F,alpha).uv(u,y)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
            .normal(p.last().normal(),0,1,0).endVertex();
    }
    private JingGuangPanWaveRenderer() {}
}
