package untamedwilds.entity;

import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.AnimationHandler;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import untamedwilds.config.ConfigGamerules;
import untamedwilds.entity.ai.control.look.SmartLandLookControl;
import untamedwilds.util.EntityUtils;

public abstract class ComplexMobTerrestrial extends ComplexMob implements IAnimatedEntity {

    public int sitProgress; // A counter which defines the progress towards the Sitting Poses
    public int ticksToSit;
    public int sleepProgress; // A counter which defines the progress towards the Sleeping Poses
    public int forceSleep; // Negative forceSleep keeps the mob awake, Positive forceSleep keeps the mob asleep
    protected int tiredCounter = 0;
    protected int buoyancy = 1;
    private static final EntityDataAccessor<Integer> HUNGER = SynchedEntityData.defineId(ComplexMobTerrestrial.class, EntityDataSerializers.INT);
    private int animationTick;
    private Animation currentAnimation;
    public float turn_speed = 0.2F;
    protected float swimSpeedMult = 1.0F;

    public ComplexMobTerrestrial(EntityType<? extends ComplexMob> type, Level worldIn){
        super(type, worldIn);
        this.moveControl = new MoveControl(this);
        this.lookControl = new SmartLandLookControl(this, 30);
        this.ticksToSit = 40;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HUNGER, 79); // One point less than the breeding threshold
    }

    public void aiStep() {
        AnimationHandler.INSTANCE.updateAnimations(this);
        if (!level.isClientSide) {
            if (this.forceSleep > 0) {
                this.forceSleep--;
            }
            else if (this.forceSleep < 0) {
                this.forceSleep++;
            }
            if (!this.getNavigation().isDone() && (this.isSitting() || this.isSleeping())) {
                this.setSitting(false);
                this.setSleeping(false);
            }

            if (this.getAirSupply() < 40 && this.tickCount % 10 == 0) { // TODO: There's probably a better place to dump this (mobs about to drown will go to the surface for air), but it refuses to work everywhere else
                this.getMoveControl().strafe(0.6F, 0);
                this.jumpFromGround();
            }

            if (!this.isSleeping() && this.forceSleep > 0) {
                this.setSleeping(true);
            }
            if (this.tickCount % 200 == 0) {
                if (!this.isActive() && this.getNavigation().isDone()) {
                    this.tiredCounter++;
                    if (this.distanceToSqr(this.getHomeAsVec()) <= 6) {
                        this.setSleeping(true);
                        this.tiredCounter = 0;
                    }
                    else if (tiredCounter >= 3) {
                        this.setHome(BlockPos.ZERO);
                        this.tiredCounter = 0;
                    }
                    this.moveControl.setWantedPosition(this.getHome().getX(), this.getHome().getY(), this.getHome().getZ(), 1f);
                }
            }
        }
        if (this.isSitting() && this.sitProgress < this.ticksToSit) {
            this.sitProgress++;
        } else if (!this.isSitting() && this.sitProgress > 0) {
            this.sitProgress--;
        }
        if (this.isSleeping() && this.sleepProgress < 40) {
            this.sleepProgress++;
        } else if (!this.isSleeping() && this.sleepProgress > 0) {
            this.sleepProgress--;
        }
        super.aiStep();
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !this.level.isClientSide()) {
            ItemStack itemstack = player.getItemInHand(hand);
            if (!this.isBaby() && this.isFood(itemstack) && !this.dead) {
                if (!this.level.isClientSide && !player.isCreative()) {
                    itemstack.shrink(1);
                }
                if (ConfigGamerules.playerBreeding.get() && this.age == 0) {
                    this.setInLove(player);
                    EntityUtils.spawnParticlesOnEntity(this.level, this, ParticleTypes.HEART, 7, 1);
                }
                this.setAnimation(this.getAnimationEat());
                this.playSound(SoundEvents.GENERIC_EAT, 1F, 1);
                return InteractionResult.CONSUME;
            }
        }

        return super.mobInteract(player, hand);
    }

    protected ActivityType getActivityType() {
        return getEntityData(this.getType()).getActivityType(this.getVariant());
    }

    public boolean isActive() {
        ActivityType type = this.getActivityType();
        Pair<Integer, Integer> times = type.getTimes();
        if ((this.isTame() && this.getCommandInt() != 0) || !ConfigGamerules.sleepBehaviour.get()) {
            return true;
        }
        if (type == ActivityType.CATHEMERAL) {
            return this.tickCount % 17000 < 3000;
        }
        long time = this.level.getDayTime();
        if (!times.getFirst().equals(times.getSecond())) {
            if (times.getFirst() > times.getSecond()) {
                return time > times.getFirst() || time < times.getSecond();
            }
            else {
                return time > times.getFirst() && time < times.getSecond();
            }
        }
        return this.forceSleep >= 0;
    }

    public enum ActivityType {
        DIURNAL         ("diurnal", 1000, 16000), // From 7 AM to 10 PM
        NOCTURNAL       ("nocturnal", 13000, 4000), // From 7 PM to 10 AM
        CREPUSCULAR     ("crepuscular", 8000, 23000), // From 14 PM to 5 AM ; 4000 - 19000 ???
        CATHEMERAL      ("cathemeral", -1, -1),      // Random naps throughout the day
        INSOMNIAC       ("insomniac", -1, -1);      // No sleep, redundant, should just not add the GoToSleepGoal

        public int wakeUp;
        public int sleep;
        public String name;
        public static final Codec<ActivityType> CODEC = Codec.STRING.comapFlatMap(ActivityType::getByName, ActivityType::toString).stable();

        ActivityType(String name, int wakeUp, int sleep) {
            this.wakeUp = wakeUp;
            this.sleep = sleep;
            this.name = name;
        }

        private static DataResult<ActivityType> getByName(String path) {
            return switch (path) {
                case "diurnal" -> DataResult.success(DIURNAL);
                case "nocturnal" -> DataResult.success(NOCTURNAL);
                case "crepuscular" -> DataResult.success(CREPUSCULAR);
                case "cathemeral" -> DataResult.success(CATHEMERAL);
                default -> DataResult.success(INSOMNIAC);
            };
        }

        public String toString() {
            return this.name;
        }

        public Pair<Integer, Integer> getTimes() {
            return new Pair<>(this.wakeUp, this.sleep);
        }
    }

    private void setHunger(int hunger){
        this.entityData.set(HUNGER, hunger);
    }
    public int getHunger(){
        return (this.entityData.get(HUNGER));
    }
    public boolean isStarving() { return this.getHunger() <= 0; }
    public void addHunger(int change) {
        int i = this.getHunger() + change;
        this.setHunger((i > 200) ? 200 : (Math.max(i, 0)));
    }

    public boolean hurt(DamageSource source, float amount) {
        if (this.isSitting()) {
            this.setSitting(false);
        }
        if (this.isSleeping()) {
            this.setSleeping(false);
            this.forceSleep = -4000;
        }
        return super.hurt(source, amount);
    }

    public void die(DamageSource p_70645_1_) {
        if (!this.level.isClientSide && !ConfigGamerules.hardcoreDeath.get() && this.getHome() != BlockPos.ZERO && this.isTame() && this.getHunger() != 0) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, 800, 0));
            this.setHealth(0.5F);
            this.setHunger(0);
            if (!this.randomTeleport(this.getHome().getX(), this.getHome().getY(), this.getHome().getZ(), true)){
                super.die(p_70645_1_);
            }
        }
        else {
            super.die(p_70645_1_);
        }
    }

    /*@Override
    public void travel(Vector3d destination) {
        if (this.isServerWorld() || this.canPassengerSteer()) {
            ModifiableAttributeInstance gravity = this.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());
            boolean flag = this.getDeltaMovement().y <= 0.0D;

            double d0 = gravity.getValue();

            FluidState fluidstate = this.level.getFluidState(this.getPosition());
            if (this.isInWater() && this.func_241208_cS_() && !this.func_230285_a_(fluidstate.getFluid())) {
                double d8 = this.getY();
                float f5 = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float f6 = 0.045F; // EDITED: Was 0.02
                float f7 = (float)EnchantmentHelper.getDepthStriderModifier(this);

                if (!this.onGround) {
                    f7 *= 0.7F;
                }

                if (f7 > 0.0F) {
                    if (f7 > 3.0F) {
                        f7 = 3.0F;
                    }
                    f5 += (0.54600006F - f5) * f7 / 3.0F;
                    f6 += (this.getAIMoveSpeed() - f6) * f7 / 3.0F;
                }

                if (this.isPotionActive(Effects.DOLPHINS_GRACE)) {
                    f5 = 0.96F;
                }

                f6 *= (float)this.getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get()).getValue();
                this.moveRelative(f6, destination);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vector3d vector3d6 = this.getDeltaMovement();
                if (this.collidedHorizontally && this.isOnLadder()) {
                    vector3d6 = new Vector3d(vector3d6.x, 0.2D, vector3d6.z);
                }

                this.setDeltaMovement(vector3d6.mul(f5, 0.8F, f5));
                Vector3d vector3d2 = this.func_233626_a_(d0, flag, this.getDeltaMovement());
                this.setDeltaMovement(vector3d2);
                if (this.collidedHorizontally && this.isOffsetPositionInLiquid(vector3d2.x, vector3d2.y + (double)0.6F - this.getY() + d8, vector3d2.z)) {
                    this.setDeltaMovement(vector3d2.x, 0.3F, vector3d2.z);
                }
            } else if (this.isInLava() && this.func_241208_cS_() && !this.func_230285_a_(fluidstate.getFluid())) {
                double d7 = this.getY();
                this.moveRelative(0.02F, destination);
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.func_233571_b_(FluidTags.LAVA) <= this.func_233579_cu_()) {
                    this.setDeltaMovement(this.getDeltaMovement().mul(0.5D, 0.8F, 0.5D));
                    this.setDeltaMovement(this.func_233626_a_(d0, flag, this.getDeltaMovement()));
                } else {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
                }

                if (!this.hasNoGravity()) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -d0 / 4.0D, 0.0D));
                }

                Vector3d vector3d4 = this.getDeltaMovement();
                if (this.collidedHorizontally && this.isOffsetPositionInLiquid(vector3d4.x, vector3d4.y + (double)0.6F - this.getY() + d7, vector3d4.z)) {
                    this.setDeltaMovement(vector3d4.x, 0.3F, vector3d4.z);
                }
            } else {
                BlockPos blockpos = this.getPositionUnderneath();
                float f3 = this.level.getBlockState(this.getPositionUnderneath()).getSlipperiness(world, this.getPositionUnderneath(), this);
                float f4 = this.onGround ? f3 * 0.91F : 0.91F;
                Vector3d vector3d5 = this.func_233633_a_(destination, f3);
                double d2 = vector3d5.y;
                if (this.isPotionActive(Effects.LEVITATION)) {
                    d2 += (0.05D * (double)(this.getActivePotionEffect(Effects.LEVITATION).getAmplifier() + 1) - vector3d5.y) * 0.2D;
                    this.fallDistance = 0.0F;
                } else if (this.level.isClientSide && !this.level.isBlockLoaded(blockpos)) {
                    if (this.getY() > 0.0D) {
                        d2 = -0.1D;
                    } else {
                        d2 = 0.0D;
                    }
                } else if (!this.hasNoGravity()) {
                    d2 -= d0;
                }

                this.setDeltaMovement(vector3d5.x * (double)f4, d2 * (double)0.98F, vector3d5.z * (double)f4);
            }
        }

        this.prevLimbSwingAmount = this.limbSwingAmount;
        double d5 = this.getX() - this.prevPosX;
        double d6 = this.getZ() - this.prevPosZ;
        double d8 = this instanceof IFlyingAnimal ? this.getY() - this.prevPosY : 0.0D;
        float f8 = Mth.sqrt((float) (d5 * d5 + d8 * d8 + d6 * d6)) * 4.0F;
        if (f8 > 1.0F) {
            f8 = 1.0F;
        }

        this.limbSwingAmount += (f8 - this.limbSwingAmount) * 0.4F;
        this.limbSwing += this.limbSwingAmount;
    }

     */

    @Override
    public int getAnimationTick() {
        return animationTick;
    }
    @Override
    public void setAnimationTick(int tick) {
        animationTick = tick;
    }
    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }
    @Override
    public void setAnimation(Animation animation) {
        currentAnimation = animation;
    }
    @Override
    public Animation[] getAnimations() {
        return new Animation[]{};
    }

    public Animation getAnimationEat() { return this.NO_ANIMATION; }

    public void addAdditionalSaveData(CompoundTag compound){
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Sleeping", this.isSleeping());
        compound.putInt("SleepingTicks", this.forceSleep);
        compound.putBoolean("Sitting", this.isSitting());
        compound.putInt("Hunger", this.getHunger());
    }

    public void readAdditionalSaveData(CompoundTag compound){
        super.readAdditionalSaveData(compound);
        this.setSleeping(compound.getBoolean("Sleeping"));
        this.forceSleep = compound.getInt("SleepingTicks");
        this.setSitting(compound.getBoolean("Sitting"));
        this.setHunger(compound.getInt("Hunger"));
    }

    /*static class MoveHelperController extends MoveControl {
        private final ComplexMobTerrestrial entity;

        MoveHelperController(ComplexMobTerrestrial turtleIn) {
            super(turtleIn);
            this.entity = turtleIn;
        }

        @Override
        public void tick() {
            if (this.action == MovementController.Action.STRAFE) {
                float f = (float)this.entity.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
                float f1 = (float)this.speed * f;
                float f2 = this.moveForward;
                float f3 = this.moveStrafe;
                float f4 = Mth.sqrt(f2 * f2 + f3 * f3);
                if (f4 < 1.0F) {
                    f4 = 1.0F;
                }

                f4 = f1 / f4;
                f2 = f2 * f4;
                f3 = f3 * f4;
                float f5 = Mth.sin(this.entity.rotationYaw * ((float)Math.PI / 180F));
                float f6 = Mth.cos(this.entity.rotationYaw * ((float)Math.PI / 180F));
                float f7 = f2 * f6 - f3 * f5;
                float f8 = f3 * f6 + f2 * f5;
                PathNavigator pathnavigator = this.entity.getNavigation();
                NodeProcessor nodeprocessor = pathnavigator.getNodeProcessor();
                if (nodeprocessor.getPathNodeType(this.entity.world, Mth.floor(this.entity.getX() + (double) f7), Mth.floor(this.entity.getY()), Mth.floor(this.entity.getZ() + (double) f8)) != PathNodeType.WALKABLE) {
                    this.moveForward = (float) this.getSpeed(); // EDITED: was hardcoded to 1.0F
                    this.moveStrafe = 0.0F;
                    f1 = f;
                }

                this.entity.setAIMoveSpeed(f1);
                this.entity.setMoveForward(this.moveForward);
                this.entity.setMoveStrafing(this.moveStrafe);
                this.action = MovementController.Action.WAIT;

            } else if (this.action == MovementController.Action.MOVE_TO) {
                this.action = MovementController.Action.WAIT;

                double d0 = this.posX - this.entity.getX();
                double d1 = this.posZ - this.entity.getZ();
                double d2 = this.posY - this.entity.getY();
                double d3 = d0 * d0 + d2 * d2 + d1 * d1;
                if (d3 < (double)2.5000003E-7F) {
                    this.entity.setMoveForward(0.0F);
                    return;
                }
                float f = (float)(Mth.atan2(d1, d0) * (double)(180F / (float)Math.PI)) - 90.0F;
                this.entity.rotationYaw = this.limitAngle(this.entity.rotationYaw, f, this.entity.turn_speed * 100);
                BlockPos blockpos = this.entity.getPosition();
                BlockState blockstate = this.entity.world.getBlockState(blockpos);
                Block block = blockstate.getBlock();
                VoxelShape voxelshape = blockstate.getCollisionShape(this.entity.world, blockpos);

                if (this.entity.isInWater()) {
                    if (this.entity.collidedHorizontally && this.entity.tickCount % 10 == 0) {
                        this.entity.jump();
                    }
                    //this.entity.setDeltaMovement(this.entity.getDeltaMovement().scale(1.1));
                    float f2 = -((float)(Mth.atan2(d1, Mth.sqrt(d0 * d0 + d2 * d2)) * (double)(180F / (float)Math.PI)));
                    f2 = Mth.clamp(Mth.wrapDegrees(f2), -85.0F, 85.0F);
                    this.entity.getXRot() = this.limitAngle(this.entity.getXRot(), f2, 1.0F);
                    float f4 = Mth.sin(this.entity.getXRot() * ((float)Math.PI / 180F));
                    this.entity.moveVertical = -f4 *  (float)this.entity.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
                    if (this.entity.getTarget() == null) {
                        this.entity.setDeltaMovement(this.entity.getDeltaMovement().add(0.0D, this.entity.buoyancy - 1, 0.0D));
                    }
                    //this.entity.getXRot() = this.limitAngle(this.entity.getXRot(), (float) (this.entity.getDeltaMovement().getY() * -40), 0.1F);
                }

                //((ServerWorld)this.entity.world).spawnParticle(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0.15F);
                float f1 = (float)(this.getSpeed() * this.entity.getAttribute(Attributes.MOVEMENT_SPEED).getValue());
                this.entity.setAIMoveSpeed(Mth.lerp(0.125F, this.entity.getAIMoveSpeed(), f1));

                if (d2 > (double)this.entity.maxUpStep && d0 * d0 + d1 * d1 < (double)Math.max(1.0F, this.entity.getBbWidth()) || !voxelshape.isEmpty() && this.entity.getY() < voxelshape.getEnd(Direction.Axis.Y) + (double)blockpos.getY() && !block.isIn(BlockTags.DOORS) && !block.isIn(BlockTags.FENCES)) {
                    this.entity.getJumpControl().jump();
                    this.action = MovementController.Action.JUMPING;
                }
            } else if (this.action == MovementController.Action.JUMPING) {
                this.entity.setAIMoveSpeed((float)(this.speed * this.entity.getAttribute(Attributes.MOVEMENT_SPEED).getValue()));
                if (this.entity.onGround) {
                    this.action = MovementController.Action.WAIT;
                }
            } else {
                if (this.entity.isInWater() && this.entity.getTarget() == null && !this.entity.collidedHorizontally) {
                    this.entity.setDeltaMovement(this.entity.getDeltaMovement().add(0.0D, this.entity.buoyancy - 1, 0.0D));
                }
                this.entity.setMoveForward(0.0F);
            }
        }
    }*/
}
