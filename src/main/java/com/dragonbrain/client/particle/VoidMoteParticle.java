package com.dragonbrain.client.particle;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

/** Slow drifting cyan mote — ambient magic/atmosphere. */
public class VoidMoteParticle extends SpriteBillboardParticle {
    protected VoidMoteParticle(ClientWorld world, double x, double y, double z,
                               double vx, double vy, double vz, SpriteProvider sprites, Random random) {
        super(world, x, y, z);
        setSprite(sprites.getSprite(random));
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.scale = 0.08f + random.nextFloat() * 0.05f;
        this.maxAge = 50 + random.nextInt(40);
        this.alpha = 0.85f;
    }

    public static net.minecraft.client.particle.ParticleFactory<SimpleParticleType> factory(
            SpriteProvider sprites) {
        return (parameters, world, x, y, z, vx, vy, vz, random) ->
                new VoidMoteParticle(world, x, y, z, vx, vy, vz, sprites, random);
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 0.85f * (1.0f - (float) this.age / this.maxAge);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }
}
