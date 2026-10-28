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

import com.dragonbrain.client.render.RiftStalkerRenderState;
import com.dragonbrain.entity.RiftStalkerEntity;

/**
 * Rift Stalker model. UV layout (64x64, must match generate_assets.py):
 * body 12x10x18 @ (0,0), head 8x8x10 @ (0,32), legs 4x12x4 @ (40,32),
 * tail 3x3x12 @ (0,52), dorsal spikes 2x6x2 @ (44,0).
 * Wind-up pose: head lowered, body crouched, tail raised (plus the
 * glowing-gill texture swap in the renderer). Recovery: head down,
 * body sway. These poses mirror RiftStalkerEntity's real states.
 */
public class RiftStalkerModel extends EntityModel<RiftStalkerRenderState> {
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart[] legs = new ModelPart[4];

    public RiftStalkerModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.tail = this.body.getChild("tail");
        String[] names = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
        for (int i = 0; i < 4; i++) {
            this.legs[i] = root.getChild(names[i]);
        }
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-6.0f, -5.0f, -9.0f, 12.0f, 10.0f, 18.0f, new Dilation(0.0f)),
                ModelTransform.origin(0.0f, 13.0f, 0.0f));
        body.addChild("head",
                ModelPartBuilder.create().uv(0, 32)
                        .cuboid(-4.0f, -4.0f, -10.0f, 8.0f, 8.0f, 10.0f),
                ModelTransform.origin(0.0f, -2.0f, -8.0f));
        body.addChild("tail",
                ModelPartBuilder.create().uv(0, 52)
                        .cuboid(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 12.0f),
                ModelTransform.origin(0.0f, -3.0f, 9.0f));
        for (int i = 0; i < 3; i++) {
            body.addChild("spike" + i,
                    ModelPartBuilder.create().uv(44, 0)
                            .cuboid(-1.0f, -6.0f, -1.0f, 2.0f, 6.0f, 2.0f),
                    ModelTransform.origin(0.0f, -5.0f, -5.0f + i * 5.0f));
        }
        float[][] legPos = {{-4, -6}, {4, -6}, {-4, 6}, {4, 6}};
        String[] names = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
        for (int i = 0; i < 4; i++) {
            root.addChild(names[i],
                    ModelPartBuilder.create().uv(40, 32)
                            .cuboid(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                    ModelTransform.origin(legPos[i][0], 12.0f, legPos[i][1]));
        }
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public void setAngles(RiftStalkerRenderState state) {
        super.setAngles(state);
        float pos = state.walkPos;
        float speed = Math.min(1.0f, state.walkSpeed);
        this.legs[0].pitch = MathHelper.cos(pos * 0.6662f) * 1.1f * speed;
        this.legs[3].pitch = MathHelper.cos(pos * 0.6662f) * 1.1f * speed;
        this.legs[1].pitch = MathHelper.cos(pos * 0.6662f + (float) Math.PI) * 1.1f * speed;
        this.legs[2].pitch = MathHelper.cos(pos * 0.6662f + (float) Math.PI) * 1.1f * speed;
        this.tail.yaw = MathHelper.sin(state.age * 0.15f) * 0.3f;
        this.head.yaw = state.relativeHeadYaw * ((float) Math.PI / 180.0f);
        this.head.pitch = state.pitch * ((float) Math.PI / 180.0f);

        switch (state.attackState) {
            case WINDUP -> {
                this.body.originY = 15.0f;               // crouch
                this.body.pitch = 0.18f;
                this.head.pitch += 0.55f;                // head down, jaws forward
                this.tail.pitch = -0.7f;                 // tail raised
            }
            case LUNGE -> {
                this.body.originY = 12.0f;
                this.body.pitch = -0.12f;
                this.tail.pitch = 0.3f;
            }
            case RECOVERY -> {
                this.body.originY = 14.5f;
                this.body.roll = MathHelper.sin(state.age * 0.3f) * 0.05f;
                this.head.pitch += 0.35f;                // exhausted, head low
                this.tail.pitch = 0.55f;                 // tail droops
            }
            default -> {
                this.body.originY = 13.0f;
                this.body.pitch = 0.0f;
                this.body.roll = 0.0f;
                this.tail.pitch = 0.0f;
            }
        }
        if (state.attackState == RiftStalkerEntity.AttackState.IDLE) {
            this.body.originY = 13.0f;
        }
    }
}
