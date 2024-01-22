package app.phtn.pulse.common.vanilla

import dev.emortal.rayfast.area.Intersection
import dev.emortal.rayfast.area.area3d.Area3d
import dev.emortal.rayfast.casting.grid.GridCast
import dev.emortal.rayfast.vector.Vector3d
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Explosion
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.time.TimeUnit
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor

class VanillaExplosion protected constructor(
    private val center: Point,
    strength: Float,
    private val blockDamage: Boolean,
    private val startsFires: Boolean,
    private val dropsEverything: Boolean
) : Explosion(
    center.x().toFloat(), center.y().toFloat(), center.z().toFloat(), strength
) {
    override fun prepare(instance: Instance): List<Point> {
        val maximumBlastRadius = strength
        val positions: MutableSet<Point> = HashSet()

        if (blockDamage) {
            for (x in 0..15) {
                for (y in 0..15) {
                    for (z in 0..15) {
                        if (!(x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15)) { // must be on outer edge of 16x16x16 cube
                            continue
                        }

                        val dir = Vec((x - 8.5f).toDouble(), (y - 8.5f).toDouble(), (z - 8.5f).toDouble()).normalize()

                        val gridIterator: Iterator<Vector3d> = GridCast.createGridIterator(
                            centerX.toDouble(), centerY.toDouble(),
                            centerZ.toDouble(), dir.x(), dir.y(), dir.z(), 1.0, maximumBlastRadius.toDouble()
                        )

                        var intensity = ((0.7f + explosionRNG.nextFloat() * 0.6f) * strength).toDouble()

                        while (gridIterator.hasNext()) {
                            val vec = gridIterator.next()
                            val pos: Point = Vec(vec.x(), vec.y(), vec.z())

                            intensity -= 0.225

                            val block = instance.loadOptionalChunk(pos).join()!!
                                .getBlock(pos)

                            val explosionResistance = block.registry().explosionResistance()
                            intensity -= (explosionResistance / 5.0)

                            if (intensity < 0) {
                                break
                            }

                            positions.add(pos)
                        }
                    }
                }
            }
        }

        val damageRadius = maximumBlastRadius // TODO: should be different from blast radius
        val potentiallyDamagedEntities = getEntitiesAround(instance, damageRadius.toDouble())

        for (entity in potentiallyDamagedEntities) {
            affect(entity, damageRadius)
        }

        if (blockDamage) {
            for (position in positions) {
                val block = instance.getBlock(position)

                if (block.isAir) {
                    continue
                }

                //                if (block.compare(Block.TNT)) {
//                    spawnPrimedTNT(instance, position, new Pos(getCenterX(), getCenterY(), getCenterZ()));
//                    continue;
//                }

//                if (customBlock != null) {
//                    if (!customBlock.onExplode(instance, position, lootTableArguments)) {
//                        continue;
//                    }
//                }
                val p = explosionRNG.nextDouble()
                val shouldDropItem = p <= 1 / strength

                if (dropsEverything || shouldDropItem) {
//                    LootTableManager lootTableManager = MinecraftServer.getLootTableManager();
//                    try {
//                        LootTable table = null;
//                        if (customBlock != null) {
//                            table = customBlock.getLootTable(lootTableManager);
//                        }
//                        if (table == null) {
//                            table = lootTableManager.load(NamespaceID.from("blocks/" + block.name().toLowerCase()));
//                        }
//                        List<ItemStack> output = table.generate(lootTableArguments);
//                        for (ItemStack out : output) {
//                            ItemEntity itemEntity = new ItemEntity(out, new Position(position.getX() + explosionRNG.nextFloat(), position.getY() + explosionRNG.nextFloat(), position.getZ() + explosionRNG.nextFloat()));
//                            itemEntity.setPickupDelay(500L, TimeUnit.MILLISECOND);
//                            itemEntity.setInstance(instance);
//                        }
//                    } catch (FileNotFoundException e) {
//                        // loot table does not exist, ignore
//                    }
                }
            }
        }

        return LinkedList(positions)
    }

    //    private void spawnPrimedTNT(Instance instance, Point blockPosition, Point explosionSource) {
    //        Pos initialPosition = new Pos(blockPosition.blockX() + 0.5f, blockPosition.blockY() + 0f, blockPosition.blockZ() + 0.5f);
    //
    //        PrimedTNT primedTNT = new PrimedTNT(10 + (TNTBlockHandler.TNT_RANDOM.nextInt(5) - 2));
    //        primedTNT.setInstance(instance);
    //        primedTNT.teleport(initialPosition);
    //
    //        Point direction = blockPosition.sub(explosionSource);
    //        double distance = explosionSource.distanceSquared(blockPosition);
    //        Vec vec = new Vec(direction.x(), direction.y(), direction.z());
    //        vec = vec.div(distance);
    //
    //        primedTNT.setVelocity(vec.mul(15));
    //    }
    override fun postSend(instance: Instance, blocks: List<Point>) {
        if (!startsFires) {
            return
        }

        val batch = AbsoluteBlockBatch()

        for (position in blocks) {
            val block = instance.getBlock(position)

            if (block.isAir && position.y() > 0) {
                if (explosionRNG.nextFloat() < 1 / 3f) {
                    val belowPos = position.add(0.0, -1.0, 0.0)

                    // check that block below is solid
                    val below = instance.getBlock(belowPos)

                    if (below.isSolid) {
                        batch.setBlock(position, Block.FIRE)
                    }
                }
            }
        }

        batch.apply(instance, null)
    }

    private fun affect(e: Entity, damageRadius: Float) {
        var exposure = calculateExposure(e, damageRadius).toDouble()
        val distance = e.position.distance(center)
        val impact = (1.0 - distance / damageRadius) * exposure
        val damage = floor((impact * impact + impact) * 7 * strength + 1)

        if (e is LivingEntity) {
            e.damage(DamageType.EXPLOSION, damage.toFloat())
        } else {
            if (e is ItemEntity) {
                e.scheduleRemove(1L, TimeUnit.SERVER_TICK)
            }
            // TODO: different entities will react differently (items despawn, boats, minecarts drop as items, etc.)
        }

        val blastProtection = 0f // TODO: apply enchantments

        exposure -= exposure * 0.15f * blastProtection

        var velocityBoost = e.position.asVec().add(0.0, e.eyeHeight, 0.0).sub(center)

        velocityBoost = velocityBoost.normalize().mul(exposure * MinecraftServer.TICK_PER_SECOND)
        e.velocity = e.velocity.add(velocityBoost)
    }

    private fun calculateExposure(e: Entity, damageRadius: Float): Float {
        val w = floor(e.boundingBox.width() * 2).toInt() + 1
        val h = floor(e.boundingBox.height() * 2).toInt() + 1
        val d = floor(e.boundingBox.depth() * 2).toInt() + 1

        val instance = e.instance
        val pos = e.position
        val entX = pos.x()
        val entY = pos.y()
        val entZ = pos.z()

        // Generate entity hitbox
        val area3d = Area3d.CONVERTER.from(e)

        var hits = 0
        val rays = w * h * d

        val wd2 = w / 2
        val dd2 = d / 2

        var dx = (-ceil(wd2.toDouble())).toInt()
        while (dx < floor(wd2.toDouble())) {
            for (dy in 0 until h) {
                var dz = (-ceil(dd2.toDouble())).toInt()
                while (dz < floor(dd2.toDouble())) {
                    val deltaX = entX + dx - centerX
                    val deltaY = entY + dy - centerY
                    val deltaZ = entZ + dz - centerZ

                    // TODO: Check for distance
                    val intersection = area3d.lineIntersection(
                        centerX.toDouble(), centerY.toDouble(), centerZ.toDouble(),
                        deltaX, deltaY, deltaZ, Intersection.ANY_3D
                    ).intersection()

                    if (intersection != null) {
                        hits++
                    }
                    dz++
                }
            }
            dx++
        }

        return hits.toFloat() / rays
    }

    private fun getEntitiesAround(instance: Instance, damageRadius: Double): List<Entity> {
        val intRadius = ceil(damageRadius).toInt()
        val affected: MutableList<Entity> = LinkedList()
        val radiusSq = damageRadius * damageRadius

        for (x in -intRadius..intRadius) {
            for (z in -intRadius..intRadius) {
                val posX = floor((centerX + x).toDouble()).toInt()
                val posZ = floor((centerZ + z).toDouble()).toInt()

                val list = instance.getChunkEntities(instance.getChunk(posX shr 4, posZ shr 4))

                for (e in list) {
                    val pos = e.position
                    val dx = pos.x() - centerX
                    val dy = pos.y() - centerY
                    val dz = pos.z() - centerZ

                    if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                        if (!affected.contains(e)) {
                            affected.add(e)
                        }
                    }
                }
            }
        }

        return affected
    }

    fun trigger(instance: Instance?) {
        this.apply(instance!!)
    }

    class Builder(private val center: Point, private val strength: Float) {
        private var dropEverything = true
        private var isFlaming = false
        private var dontDestroyBlocks = false

        fun dropEverything(dropEverything: Boolean): Builder {
            this.dropEverything = dropEverything
            return this
        }

        fun isFlaming(isFlaming: Boolean): Builder {
            this.isFlaming = isFlaming
            return this
        }

        fun destroyBlocks(dontDestroyBlocks: Boolean): Builder {
            this.dontDestroyBlocks = !dontDestroyBlocks
            return this
        }

        fun build(): VanillaExplosion {
            return VanillaExplosion(center, strength, dropEverything, isFlaming, dontDestroyBlocks)
        }
    }

    companion object {
        const val DROP_EVERYTHING_KEY: String = "minestom:drop_everything"
        const val IS_FLAMING_KEY: String = "minestom:is_flaming"
        const val DONT_DESTROY_BLOCKS_KEY: String = "minestom:no_block_damage"
        private val explosionRNG = Random()

        const val THREAD_POOL_NAME: String = "MSVanilla-Explosion"
        const val THREAD_POOL_COUNT: Int = 2
        fun builder(center: Point, strength: Float): Builder {
            return Builder(center, strength)
        }
    }
}