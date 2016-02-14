package mcjty.aquamunda.fluid;

import io.netty.buffer.ByteBuf;
import mcjty.aquamunda.blocks.ModBlocks;
import mcjty.aquamunda.network.NetworkTools;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EntityFallingFreshWaterBlock extends Entity implements IEntityAdditionalSpawnData {
    private IBlockState fallTile;
    public int fallTime;
    private boolean canSetAsBlock;

    public EntityFallingFreshWaterBlock(World worldIn) {
        super(worldIn);
    }

    public EntityFallingFreshWaterBlock(World worldIn, double x, double y, double z, IBlockState fallingBlockState) {
        super(worldIn);
        this.fallTile = fallingBlockState;

        this.preventEntitySpawning = true;
        this.setSize(0.98F, 0.98F);
        this.setPosition(x, y, z);
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    /**
     * returns if this entity triggers Block.onEntityWalking on the blocks they walk on. used for spiders and wolves to
     * prevent them from trampling crops
     */
    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    protected void entityInit() {
    }

    /**
     * Returns true if other Entities should be prevented from moving through this Entity.
     */
    @Override
    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void onUpdate() {
        Block block = this.fallTile.getBlock();

        if (block.getMaterial() == Material.air) {
            this.setDead();
        } else {
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;

            if (this.fallTime++ == 0) {
                BlockPos blockpos = new BlockPos(this);

                if (this.worldObj.getBlockState(blockpos).getBlock() == block) {
                    this.worldObj.setBlockToAir(blockpos);
                } else if (!this.worldObj.isRemote) {
                    this.setDead();
                    return;
                }
            }

            this.motionY -= 0.03999999910593033D;
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.9800000190734863D;
            this.motionY *= 0.9800000190734863D;
            this.motionZ *= 0.9800000190734863D;

            if (!this.worldObj.isRemote) {
                BlockPos blockpos1 = new BlockPos(this);

                if (this.onGround) {
                    this.motionX *= 0.699999988079071D;
                    this.motionZ *= 0.699999988079071D;
                    this.motionY *= -0.5D;

                    if (this.worldObj.getBlockState(blockpos1).getBlock() != Blocks.piston_extension) {
                        this.setDead();

                        if (!this.canSetAsBlock) {
                            if (this.worldObj.canBlockBePlaced(block, blockpos1, true, EnumFacing.UP, null, null)) {
                                if (!BlockFalling.canFallInto(this.worldObj, blockpos1.down()) && this.worldObj.setBlockState(blockpos1, this.fallTile, 3)) {
                                    if (block instanceof BlockFalling) {
                                        ((BlockFalling) block).onEndFalling(this.worldObj, blockpos1);
                                    }
                                }
                            }
                        }
                    }
                } else if (this.fallTime > 100 && !this.worldObj.isRemote && (blockpos1.getY() < 1 || blockpos1.getY() > 256) || this.fallTime > 600) {
                    this.setDead();
                }
            }
        }
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
    }

    /**
     * (abstract) Protected helper method to write subclass entity data to NBT.
     */
    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {
        Block block = this.fallTile != null ? this.fallTile.getBlock() : Blocks.air;
        ResourceLocation resourcelocation = Block.blockRegistry.getNameForObject(block);
        tagCompound.setString("Block", resourcelocation == null ? "" : resourcelocation.toString());
        tagCompound.setByte("Data", (byte) block.getMetaFromState(this.fallTile));
        tagCompound.setByte("Time", (byte) this.fallTime);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompund) {
        int i = tagCompund.getByte("Data") & 255;

        this.fallTile = ModBlocks.blockFreshWater.getStateFromMeta(i);
        this.fallTime = tagCompund.getByte("Time") & 255;
        Block block = this.fallTile.getBlock();

        if (block == null || block.getMaterial() == Material.air) {
            this.fallTile = ModBlocks.blockFreshWater.getDefaultState();
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        Block block = this.fallTile != null ? this.fallTile.getBlock() : Blocks.air;
        buffer.writeByte((byte) block.getMetaFromState(this.fallTile));
        buffer.writeByte((byte) this.fallTime);
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        int i = additionalData.readByte() & 255;
        this.fallTile = ModBlocks.blockFreshWater.getStateFromMeta(i);
        this.fallTime = additionalData.readByte() & 255;
        Block block = this.fallTile.getBlock();

        if (block == null || block.getMaterial() == Material.air) {
            this.fallTile = ModBlocks.blockFreshWater.getDefaultState();
        }

    }

    @Override
    public void addEntityCrashInfo(CrashReportCategory category) {
        super.addEntityCrashInfo(category);

        if (this.fallTile != null) {
            Block block = this.fallTile.getBlock();
            category.addCrashSection("Immitating block ID", Integer.valueOf(Block.getIdFromBlock(block)));
            category.addCrashSection("Immitating block data", Integer.valueOf(block.getMetaFromState(this.fallTile)));
        }
    }

    @SideOnly(Side.CLIENT)
    public World getWorldObj() {
        return this.worldObj;
    }

    /**
     * Return whether this entity should be rendered as on fire.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean canRenderOnFire() {
        return false;
    }

    public IBlockState getBlock() {
        return this.fallTile;
    }
}