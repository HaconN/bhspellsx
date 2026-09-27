package net.offkung.bhspellsx.client.renderer.jing_guang_pan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import static net.offkung.bhspellsx.client.renderer.jing_guang_pan.JingGuangPanHaloConstants.*;

/** Upright body-yaw halo; visual only, never reads Epic Fight joint transforms. */
public final class JingGuangPanHaloRenderer {
    static void render(RenderLevelStageEvent event, Player player, JingGuangPanHaloVfx.Visual visual) {
        float partial=event.getPartialTick();
        Vec3 camera=event.getCamera().getPosition();
        Vec3 feet=player.getPosition(partial);
        if (camera.distanceToSqr(feet)>RENDER_DISTANCE*RENDER_DISTANCE) return;
        float yaw=Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
        double radians=Math.toRadians(yaw);
        Vec3 back=new Vec3(Math.sin(radians),0,-Math.cos(radians));
        Vec3 center=feet.add(0,CENTER_Y,0).add(back.scale(BODY_HALF_DEPTH+BACK_GAP));
        double rearDot=camera.subtract(center).normalize().dot(back);
        // Only the owner needs rear-view attenuation to keep their own model readable.
        // Observers retain the same inner-light passes/intensity as a front view.
        boolean ownHalo=player==Minecraft.getInstance().player;
        double rear=ownHalo ? smooth((rearDot-BACK_FADE_START_DOT)/(BACK_FADE_FULL_DOT-BACK_FADE_START_DOT)) : 0;
        double innerGain=1-(1-BACK_INNER_MIN)*rear;
        double age=visual.age(partial);
        float alpha=visual.alpha(partial);
        if (alpha<=0) return;
        float pulse=(float)(1+PULSE_AMOUNT*Math.sin(age*2*Math.PI/PULSE_TICKS));
        double scale=OPEN_SCALE+(1-OPEN_SCALE)*smooth(age/OPEN_TICKS);
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack p=event.getPoseStack(); p.pushPose();
        try {
            p.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
            p.mulPose(Axis.YP.rotationDegrees(-yaw)); p.scale((float)scale,(float)scale,(float)scale);
            // Owner rear views keep only additive inner light; observers keep both passes.
            plane(p,buffers.getBuffer(JingGuangPanHaloRenderTypes.get("halo_inner",false)),
                    (float)(INNER_ALPHA*innerGain*(1-rear))*alpha*pulse);
            buffers.endBatch(JingGuangPanHaloRenderTypes.get("halo_inner",false));
            plane(p,buffers.getBuffer(JingGuangPanHaloRenderTypes.get("halo_inner",true)),
                    (float)(INNER_ADDITIVE*innerGain)*alpha*pulse);
            buffers.endBatch(JingGuangPanHaloRenderTypes.get("halo_inner",true));
            p.pushPose(); p.mulPose(Axis.ZP.rotationDegrees((float)(age*ROTATION_DEGREES_PER_SECOND/20)));
            plane(p,buffers.getBuffer(JingGuangPanHaloRenderTypes.get("halo_rays",true)),RAYS_ALPHA*alpha*pulse);
            p.popPose(); buffers.endBatch(JingGuangPanHaloRenderTypes.get("halo_rays",true));
            plane(p,buffers.getBuffer(JingGuangPanHaloRenderTypes.get("halo_ring",false)),RING_ALPHA*alpha);
            buffers.endBatch(JingGuangPanHaloRenderTypes.get("halo_ring",false));
            plane(p,buffers.getBuffer(JingGuangPanHaloRenderTypes.get("halo_ring",true)),GLOW_ALPHA*alpha*pulse);
            buffers.endBatch(JingGuangPanHaloRenderTypes.get("halo_ring",true));
            // Thin solid rim thickness makes an exactly edge-on view readable.
            var v=buffers.getBuffer(JingGuangPanHaloRenderTypes.get("halo_spark",false));
            for (int i=0;i<RING_SEGMENTS;i++) for (int j=0;j<TUBE_SEGMENTS;j++) {
                for (int[] corner:new int[][]{{i,j},{i+1,j},{i+1,j+1},{i,j+1}}) {
                    double t=corner[0]*2*Math.PI/RING_SEGMENTS, q=corner[1]*2*Math.PI/TUBE_SEGMENTS;
                    double r=DIAMETER/2+RIM_TUBE_RADIUS*Math.cos(q);
                    vertex(p,v,new Vec3(r*Math.cos(t),r*Math.sin(t),RIM_TUBE_RADIUS*Math.sin(q)),.5f,.5f,alpha,CREAM);
                }
            }
            buffers.endBatch(JingGuangPanHaloRenderTypes.get("halo_spark",false));
            Random random=new Random(SPARK_SEED);
            Vector3f right=new Vector3f(1,0,0).rotate(event.getCamera().rotation());
            Vector3f up=new Vector3f(0,1,0).rotate(event.getCamera().rotation());
            // Transform camera axes into halo-local coordinates so sparks remain billboards.
            right.rotateY((float)radians); up.rotateY((float)radians);
            v=buffers.getBuffer(JingGuangPanHaloRenderTypes.get("halo_spark",true));
            for (int i=0;i<SPARK_COUNT;i++) {
                double t=random.nextDouble()*2*Math.PI+Math.toRadians(age*ROTATION_DEGREES_PER_SECOND/20);
                double r=SPARK_RADIUS_MIN+random.nextDouble()*(SPARK_RADIUS_MAX-SPARK_RADIUS_MIN);
                Vec3 c=new Vec3(r*Math.cos(t),r*Math.sin(t),(random.nextDouble()*2-1)*SPARK_DEPTH);
                double size=SPARK_SIZE*(SPARK_SCALE_MIN+random.nextDouble()*(SPARK_SCALE_MAX-SPARK_SCALE_MIN))/2;
                Vec3 x=new Vec3(right.x,right.y,right.z).scale(size), y=new Vec3(up.x,up.y,up.z).scale(size);
                quad(p,v,c.subtract(x).add(y),c.add(x).add(y),c.add(x).subtract(y),c.subtract(x).subtract(y),SPARK_ALPHA*alpha*pulse);
            }
            buffers.endBatch(JingGuangPanHaloRenderTypes.get("halo_spark",true));
        } finally { p.popPose(); }
    }
    private static void plane(PoseStack p,VertexConsumer v,float alpha) {
        double r=RAY_RADIUS;
        quad(p,v,new Vec3(-r,r,0),new Vec3(r,r,0),new Vec3(r,-r,0),new Vec3(-r,-r,0),alpha);
    }
    private static void quad(PoseStack p,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float alpha) {
        vertex(p,v,a,0,0,alpha,0xffffff); vertex(p,v,b,1,0,alpha,0xffffff);
        vertex(p,v,c,1,1,alpha,0xffffff); vertex(p,v,d,0,1,alpha,0xffffff);
    }
    private static void vertex(PoseStack p,VertexConsumer v,Vec3 a,float u,float y,float alpha,int rgb) {
        v.vertex(p.last().pose(),(float)a.x,(float)a.y,(float)a.z)
            .color((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,Math.min(1,alpha)).uv(u,y)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
            .normal(p.last().normal(),0,0,1).endVertex();
    }
    private JingGuangPanHaloRenderer() {}
}
