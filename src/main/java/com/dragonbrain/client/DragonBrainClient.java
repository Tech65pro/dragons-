package com.dragonbrain.client;

import net.minecraft.client.render.RenderLayer;

import com.dragonbrain.client.model.AncientSentinelModel;
import com.dragonbrain.client.model.EndWispModel;
import com.dragonbrain.client.model.RiftStalkerModel;
import com.dragonbrain.client.particle.ResonanceSparkParticle;
import com.dragonbrain.client.particle.VoidMoteParticle;
import com.dragonbrain.client.render.AncientSentinelRenderer;
import com.dragonbrain.client.render.EndWispRenderer;
import com.dragonbrain.client.render.RiftStalkerRenderer;
import com.dragonbrain.registry.ModBlocks;
import com.dragonbrain.registry.ModEntities;
import com.dragonbrain.registry.ModParticles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client registrations for the visual overhaul: model layers, renderers,
 * particle factories and block render layers. No OptiFine/Continuity/
 * CEM/CIT/shader dependency is required anywhere.
 */
public class DragonBrainClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.END_WISP,
                EndWispModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.RIFT_STALKER,
                RiftStalkerModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.ANCIENT_SENTINEL,
                AncientSentinelModel::getTexturedModelData);

        EntityRendererRegistry.register(ModEntities.END_WISP, EndWispRenderer::new);
        EntityRendererRegistry.register(ModEntities.RIFT_STALKER, RiftStalkerRenderer::new);
        EntityRendererRegistry.register(ModEntities.ANCIENT_SENTINEL, AncientSentinelRenderer::new);

        ParticleFactoryRegistry.getInstance().register(ModParticles.VOID_MOTE,
                VoidMoteParticle::factory);
        ParticleFactoryRegistry.getInstance().register(ModParticles.RESONANCE_SPARK,
                ResonanceSparkParticle::factory);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.VOID_LILY, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CHORUS_ROOTS, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CHORUS_CANOPY, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.VOIDGLASS, RenderLayer.getTranslucent());
    }
}
