package com.dragonbrain.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.client.ModModelLayers;
import com.dragonbrain.client.model.AncientSentinelModel;
import com.dragonbrain.entity.AncientSentinelEntity;

public class AncientSentinelRenderer
        extends MobEntityRenderer<AncientSentinelEntity, AncientSentinelRenderState, AncientSentinelModel> {
    private static final Identifier TEXTURE = DragonBrain.id("textures/entity/ancient_sentinel.png");
    private static final Identifier EYES = DragonBrain.id("textures/entity/ancient_sentinel_eyes.png");

    public AncientSentinelRenderer(EntityRendererFactory.Context context) {
        super(context, new AncientSentinelModel(context.getPart(ModModelLayers.ANCIENT_SENTINEL)), 0.8f);
        this.addFeature(new EndWispRenderer.EyesFeature<>(this, EYES));
    }

    @Override
    public AncientSentinelRenderState createRenderState() {
        return new AncientSentinelRenderState();
    }

    @Override
    public void updateRenderState(AncientSentinelEntity entity, AncientSentinelRenderState state,
                                  float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.walkPos = entity.limbAnimator.getAnimationProgress(tickDelta);
        state.walkSpeed = entity.limbAnimator.getSpeed();
        state.handSwingProgress = entity.getHandSwingProgress(tickDelta);
        state.guarding = entity.isGuarding();
        state.awakened = entity.isAwakened();
        state.staggered = entity.isStaggered();
    }

    @Override
    public Identifier getTexture(AncientSentinelRenderState state) {
        return TEXTURE;
    }
}
