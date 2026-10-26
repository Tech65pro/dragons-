package com.dragonbrain.client.particle;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

/** Short-lived magenta spark — energy/telegraph feedback. */
public class ResonanceSparkParticle extends SpriteBillboardParticle {
    protected ResonanceSparkParticle(ClientWorld world, double x, double y, double z,
                                     double vx, double vy, double vz,
                                     SpriteProvider sprites, Random random) {
        super(world, x, y, z);
        setSprite(sprites.getSprite(random));
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.velocityMultiplier = 0.92f;
        this.scale = 0.07f + random.nextFloat() * 0.04f;
        this.maxAge = 14 + random.nextInt(10);
    }

    public static net.minecraft.client.particle.ParticleFactory<SimpleParticleType> factory(
            SpriteProvider sprites) {
        return (parameters, world, x, y, z, vx, vy, vz, random) ->
                new ResonanceSparkParticle(world, x, y, z, vx, vy, vz, sprites, random);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE;
    }
}
