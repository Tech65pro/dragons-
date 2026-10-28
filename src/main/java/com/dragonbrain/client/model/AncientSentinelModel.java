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

import com.dragonbrain.client.render.AncientSentinelRenderState;

/**
 * Ancient Sentinel model. UV layout (128x128, must match
 * generate_assets.py): torso 16x20x10 @ (0,0), head 10x10x10 @ (0,36),
 * arms 6x22x6 @ (56,0), legs 7x16x7 @ (0,60), crest 2x8x12 @ (88,0).
 * Poses implemented for the entity's real states: dormant (slumped),
 * awakened idle, guard (arms crossed up), stagger (head/torso drop),
 * walk cycle and right-arm attack swing.
 */
public class AncientSentinelModel extends EntityModel<AncientSentinelRenderState> {
    private final ModelPart torso;
    private final ModelPart head;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public AncientSentinelModel(ModelPart root) {
        super(root);
        this.torso = root.getChild("torso");
        this.head = this.torso.getChild("head");
        this.armLeft = this.torso.getChild("arm_left");
        this.armRight = this.torso.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData torso = root.addChild("torso",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-8.0f, -20.0f, -5.0f, 16.0f, 20.0f, 10.0f, new Dilation(0.0f)),
                ModelTransform.origin(0.0f, 16.0f, 0.0f));
        ModelPartData head = torso.addChild("head",
                ModelPartBuilder.create().uv(0, 36)
                        .cuboid(-5.0f, -10.0f, -5.0f, 10.0f, 10.0f, 10.0f),
                ModelTransform.origin(0.0f, -20.0f, 0.0f));
        head.addChild("crest",
                ModelPartBuilder.create().uv(88, 0)
                        .cuboid(-1.0f, -8.0f, -6.0f, 2.0f, 8.0f, 12.0f),
                ModelTransform.origin(0.0f, -10.0f, 0.0f));
        torso.addChild("arm_left",
                ModelPartBuilder.create().uv(56, 0)
                        .cuboid(-3.0f, 0.0f, -3.0f, 6.0f, 22.0f, 6.0f),
                ModelTransform.origin(-11.0f, -18.0f, 0.0f));
        torso.addChild("arm_right",
                ModelPartBuilder.create().mirrored().uv(56, 0)
                        .cuboid(-3.0f, 0.0f, -3.0f, 6.0f, 22.0f, 6.0f),
                ModelTransform.origin(11.0f, -18.0f, 0.0f));
        root.addChild("leg_left",
                ModelPartBuilder.create().uv(0, 60)
                        .cuboid(-3.5f, 0.0f, -3.5f, 7.0f, 16.0f, 7.0f),
                ModelTransform.origin(-4.5f, 8.0f, 0.0f));
        root.addChild("leg_right",
                ModelPartBuilder.create().mirrored().uv(0, 60)
                        .cuboid(-3.5f, 0.0f, -3.5f, 7.0f, 16.0f, 7.0f),
                ModelTransform.origin(4.5f, 8.0f, 0.0f));
        return TexturedModelData.of(data, 128, 128);
    }

    @Override
    public void setAngles(AncientSentinelRenderState state) {
        super.setAngles(state);
        float pos = state.walkPos;
        float speed = Math.min(1.0f, state.walkSpeed);
        this.legLeft.pitch = MathHelper.cos(pos * 0.5f) * 0.7f * speed;
        this.legRight.pitch = MathHelper.cos(pos * 0.5f + (float) Math.PI) * 0.7f * speed;
        this.head.yaw = state.relativeHeadYaw * ((float) Math.PI / 180.0f);
        this.head.pitch = state.pitch * ((float) Math.PI / 180.0f);

        // idle sway / attack swing
        this.armLeft.pitch = MathHelper.cos(pos * 0.5f + (float) Math.PI) * 0.4f * speed
                + MathHelper.sin(state.age * 0.05f) * 0.03f;
        this.armRight.pitch = MathHelper.cos(pos * 0.5f) * 0.4f * speed;
        if (state.handSwingProgress > 0.0f) {
            this.armRight.pitch = -2.2f * MathHelper.sin(state.handSwingProgress * (float) Math.PI);
        }
        this.armLeft.roll = 0.08f;
        this.armRight.roll = -0.08f;
        this.torso.pitch = 0.0f;
        this.torso.originY = 16.0f;

        if (!state.awakened) {          // dormant statue slump
            this.torso.pitch = 0.22f;
            this.torso.originY = 17.5f;
            this.head.pitch += 0.5f;
            this.armLeft.pitch = 0.15f;
            this.armRight.pitch = 0.15f;
        }
        if (state.guarding) {           // both forearms raised across the chest core
            this.armLeft.pitch = -1.35f;
            this.armRight.pitch = -1.35f;
            this.armLeft.roll = -0.55f;
            this.armRight.roll = 0.55f;
            this.torso.pitch = 0.08f;
        }
        if (state.staggered) {          // heavy interruption
            this.torso.pitch = 0.4f;
            this.torso.originY = 18.0f;
            this.head.pitch += 0.7f;
            this.armLeft.pitch = 0.5f;
            this.armRight.pitch = 0.5f;
            this.armLeft.roll = 0.3f;
            this.armRight.roll = -0.3f;
        }
    }
}
