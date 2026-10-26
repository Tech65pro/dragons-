package com.dragonbrain.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.util.Identifier;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.client.ModModelLayers;
import com.dragonbrain.client.model.EndWispModel;
import com.dragonbrain.entity.EndWispEntity;

public class EndWispRenderer extends MobEntityRenderer<EndWispEntity, EndWispRenderState, EndWispModel> {
    private static final Identifier TEXTURE = DragonBrain.id("textures/entity/end_wisp.png");
    private static final Identifier EYES = DragonBrain.id("textures/entity/end_wisp_eyes.png");

    public EndWispRenderer(EntityRendererFactory.Context context) {
        super(context, new EndWispModel(context.getPart(ModModelLayers.END_WISP)), 0.35f);
        this.addFeature(new EyesFeature<EndWispRenderState, EndWispModel>(this, EYES));
    }

    @Override
    public EndWispRenderState createRenderState() {
        return new EndWispRenderState();
    }

    @Override
    public void updateRenderState(EndWispEntity entity, EndWispRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.flapPos = entity.age + tickDelta;
    }

    @Override
    public Identifier getTexture(EndWispRenderState state) {
        return TEXTURE;
    }

    /** Emissive eyes overlay (eyes texture: only the face pixels are opaque). */
    static class EyesFeature<S extends net.minecraft.client.render.entity.state.LivingEntityRenderState,
            M extends net.minecraft.client.render.entity.model.EntityModel<S>>
            extends EyesFeatureRenderer<S, M> {
        private final RenderLayer layer;

        EyesFeature(FeatureRendererContext<S, M> context, Identifier texture) {
            super(context);
            this.layer = RenderLayer.getEyes(texture);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return this.layer;
        }
    }
}
