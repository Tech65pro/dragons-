package com.dragonbrain.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.client.ModModelLayers;
import com.dragonbrain.client.model.RiftStalkerModel;
import com.dragonbrain.entity.RiftStalkerEntity;

public class RiftStalkerRenderer
        extends MobEntityRenderer<RiftStalkerEntity, RiftStalkerRenderState, RiftStalkerModel> {
    private static final Identifier TEXTURE = DragonBrain.id("textures/entity/rift_stalker.png");
    private static final Identifier TEXTURE_WINDUP = DragonBrain.id("textures/entity/rift_stalker_windup.png");

    public RiftStalkerRenderer(EntityRendererFactory.Context context) {
        super(context, new RiftStalkerModel(context.getPart(ModModelLayers.RIFT_STALKER)), 0.6f);
    }

    @Override
    public RiftStalkerRenderState createRenderState() {
        return new RiftStalkerRenderState();
    }

    @Override
    public void updateRenderState(RiftStalkerEntity entity, RiftStalkerRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.walkPos = entity.limbAnimator.getPos(tickDelta);
        state.walkSpeed = entity.limbAnimator.getSpeed(tickDelta);
        state.attackState = entity.getAttackState();
    }

    @Override
    public Identifier getTexture(RiftStalkerRenderState state) {
        // Attack telegraph: glowing gills appear only during the real WINDUP state.
        return state.attackState == RiftStalkerEntity.AttackState.WINDUP ? TEXTURE_WINDUP : TEXTURE;
    }
}
