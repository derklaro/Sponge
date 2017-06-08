/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.entitycollisions;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.world.LocatableBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.event.tracking.CauseTracker;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.mixin.plugin.entitycollisions.interfaces.IModData_Collisions;

import java.util.List;

@Mixin(net.minecraft.world.chunk.Chunk.class)
public class MixinChunk_Collisions {

    @Shadow @Final private World world;

    @Inject(method = "getEntitiesWithinAABBForEntity",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false), cancellable = true)
    public void onAddCollisionEntity(Entity entityIn, AxisAlignedBB aabb, List<Entity> listToFill, Predicate<? super Entity> predicate,
            CallbackInfo ci) {
        // ignore players and entities with parts (ex. EnderDragon)
        if (this.world.isRemote || entityIn == null || entityIn instanceof EntityPlayer || entityIn.getParts() != null) {
            return;
        }

        if (!allowEntityCollision(listToFill)) {
            ci.cancel();
        }
    }

    @Inject(method = "getEntitiesOfTypeWithinAABB",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false), cancellable = true)
    public <T extends Entity> void onAddCollisionEntity(Class<? extends T> entityClass, AxisAlignedBB aabb, List<T> listToFill,
            Predicate<? super T> p_177430_4_, CallbackInfo ci) {
        // ignore player checks
        // ignore item check (ex. Hoppers)
        if (this.world.isRemote || EntityPlayer.class.isAssignableFrom(entityClass) || EntityItem.class == entityClass) {
            return;
        }

        if (!allowEntityCollision(listToFill)) {
            ci.cancel();
        }
    }

    private <T extends Entity> boolean allowEntityCollision(List<T> listToFill) {
        if (this.world instanceof IMixinWorldServer) {
            IMixinWorldServer spongeWorld = (IMixinWorldServer) this.world;
            if (spongeWorld.isProcessingExplosion()) {
                // allow explosions
                return true;
            }

            final PhaseContext phaseContext = CauseTracker.getInstance().getCurrentContext();
            LocatableBlock locatable = phaseContext.getSource(LocatableBlock.class).orElse(null);
            if (locatable != null) {
                BlockType blockType =locatable.getLocation().getBlockType();
                IModData_Collisions spongeBlock = (IModData_Collisions) blockType;
                if (spongeBlock.requiresCollisionsCacheRefresh()) {
                    spongeBlock.initializeCollisionState(this.world);
                    spongeBlock.requiresCollisionsCacheRefresh(false);
                }

                return !((spongeBlock.getMaxCollisions() >= 0) && (listToFill.size() >= spongeBlock.getMaxCollisions()));
            } else {
                IModData_Collisions spongeEntity = phaseContext.getSource(IModData_Collisions.class).orElse(null);
                if (spongeEntity != null) {
                    if (spongeEntity.requiresCollisionsCacheRefresh()) {
                        spongeEntity.initializeCollisionState(this.world);
                        spongeEntity.requiresCollisionsCacheRefresh(false);
                    }

                    return !((spongeEntity.getMaxCollisions() >= 0) && (listToFill.size() >= spongeEntity.getMaxCollisions()));
                }
                return true;
            }
        }

        return true;
    }
}
