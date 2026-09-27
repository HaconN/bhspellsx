package net.offkung.bhspellsx.client.renderer.jing_guang_pan;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Same recognized shader/state layout as the existing ring renderer; no shaderpack dependency. */
public final class JingGuangPanRenderTypes {
    private static final Map<String,RenderType> CACHE=new HashMap<>();
    public static RenderType get(String name, boolean additive) {
        return CACHE.computeIfAbsent(name+additive,key -> {
            var texture=ResourceLocation.fromNamespaceAndPath("bhspellsx","textures/vfx/jing_guang_pan/"+name+".png");
            var blend=new RenderStateShard.TransparencyStateShard("jing_guang_pan_"+additive,() -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                        additive ? GlStateManager.DestFactor.ONE : GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            },() -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
            var state=RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture,true,false))
                .setTransparencyState(blend).setCullState(new RenderStateShard.CullStateShard(false))
                .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true,false)).createCompositeState(false);
            return RenderType.create("jing_guang_pan_"+key,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,true,state);
        });
    }
    private JingGuangPanRenderTypes() {}
}
