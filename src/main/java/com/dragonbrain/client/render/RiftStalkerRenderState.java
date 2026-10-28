package com.dragonbrain.client.render;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;

import com.dragonbrain.entity.RiftStalkerEntity;

public class RiftStalkerRenderState extends LivingEntityRenderState {
    public float walkPos;
    public float walkSpeed;
    public RiftStalkerEntity.AttackState attackState = RiftStalkerEntity.AttackState.IDLE;
}
