package com.dragonbrain.client.model;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.util.math.MathHelper;

import com.dragonbrain.client.render.EndWispRenderState;

/**
 * End Wisp model. UV layout (64x64, must match tools/generate_assets.py):
 * core 8x8x8 @ (0,0) with the face on the front (+Z) face,
 * crown spikes 2x4x2 @ (32,0), tail 6x8x6 @ (0,20), fins 1x4x6 @ (40,10).
 * Animated: gentle bob, fin flutter, tail sway, spike wobble.
 */
public class EndWispModel extends EntityModel<EndWispRenderState> {
    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart finLeft;
    private final ModelPart finRight;
    private final ModelPart[] spikes = new ModelPart[4];

    public EndWispModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.tail = this.body.getChild("tail");
        this.finLeft = this.body.getChild("fin_left");
        this.finRight = this.body.getChild("fin_right");
        for (int i = 0; i < 4; i++) {
            this.spikes[i] = this.body.getChild("spike" + i);
        }
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, new Dilation(0.0f)),
                ModelTransform.origin(0.0f, 14.0f, 0.0f));
        body.addChild("tail",
                ModelPartBuilder.create().uv(0, 20)
                        .cuboid(-3.0f, 0.0f, -3.0f, 6.0f, 8.0f, 6.0f, new Dilation(-0.5f)),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));
        body.addChild("fin_left",
                ModelPartBuilder.create().uv(40, 10)
                        .cuboid(-1.0f, -2.0f, -3.0f, 1.0f, 4.0f, 6.0f),
                ModelTransform.origin(-4.0f, -5.0f, 0.0f));
        body.addChild("fin_right",
                ModelPartBuilder.create().mirrored().uv(40, 10)
                        .cuboid(0.0f, -2.0f, -3.0f, 1.0f, 4.0f, 6.0f),
                ModelTransform.origin(4.0f, -5.0f, 0.0f));
        float[][] spikePos = {{-2.5f, -2.5f}, {2.5f, -2.5f}, {-2.5f, 2.5f}, {2.5f, 2.5f}};
        for (int i = 0; i < 4; i++) {
            body.addChild("spike" + i,
                    ModelPartBuilder.create().uv(32, 0)
                            .cuboid(-1.0f, -4.0f, -1.0f, 2.0f, 4.0f, 2.0f),
                    ModelTransform.origin(spikePos[i][0], -8.0f, spikePos[i][1]));
        }
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public void setAngles(EndWispRenderState state) {
        super.setAngles(state);
        float t = state.age;
        this.body.originY = 14.0f + MathHelper.sin(t * 0.12f) * 1.2f;
        this.body.roll = MathHelper.sin(t * 0.07f) * 0.08f;
        this.tail.pitch = MathHelper.sin(t * 0.18f) * 0.35f;
        this.tail.yaw = MathHelper.cos(t * 0.14f) * 0.25f;
        float flutter = MathHelper.sin(t * 0.9f) * 0.55f;
        this.finLeft.roll = -0.35f - flutter;
        this.finRight.roll = 0.35f + flutter;
        for (int i = 0; i < 4; i++) {
            this.spikes[i].pitch = MathHelper.sin(t * 0.2f + i * 1.7f) * 0.12f;
        }
    }
}
