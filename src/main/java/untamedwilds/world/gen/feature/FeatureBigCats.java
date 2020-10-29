package untamedwilds.world.gen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import untamedwilds.entity.mammal.bigcat.AbstractBigCat;
import untamedwilds.init.ModEntity;
import untamedwilds.world.FaunaSpawn;

import java.util.Random;

public class FeatureBigCats extends Feature<NoFeatureConfig> {

    public FeatureBigCats(Codec<NoFeatureConfig> codec) {
        super(codec);
    }

    public boolean func_241855_a(ISeedReader world, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config) {
        if (rand.nextFloat() > 0.85) {
            Biome biome = world.getBiome(pos);
            EntityType<? extends AbstractBigCat> type = AbstractBigCat.SpeciesBigCat.getSpeciesByBiome(biome);
            int groupSize = 1;
            if (type == ModEntity.LION || type == ModEntity.CAVE_LION) {
                groupSize += 1 + rand.nextInt(4);
            }
            int diff = Math.abs(pos.getY() - world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, pos).getY());
            if (diff >= 10) {
                FaunaSpawn.performWorldGenSpawning(type, EntitySpawnPlacementRegistry.PlacementType.NO_RESTRICTIONS, world, biome, world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, pos).up(4), rand, groupSize);
                return false;
            }
            FaunaSpawn.performWorldGenSpawning(type, EntitySpawnPlacementRegistry.PlacementType.NO_RESTRICTIONS, world, biome, pos, rand, groupSize);
            return true;
        }
        return false;
    }
}
