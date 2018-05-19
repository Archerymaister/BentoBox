package us.tastybento.bskyblock.listeners;

import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.util.Vector;

import us.tastybento.bskyblock.BSkyBlock;
import us.tastybento.bskyblock.api.user.User;
import us.tastybento.bskyblock.util.teleport.SafeTeleportBuilder;

public class NetherPortals implements Listener {
    private static final String ERROR_NO_PERMISSION = "general.errors.no-permission";
    private final BSkyBlock plugin;
    private World world;
    private World nether;
    private World theEnd;

    public NetherPortals(BSkyBlock plugin) {
        this.plugin = plugin;
        world = plugin.getIslandWorldManager().getIslandWorld();
        nether = plugin.getIslandWorldManager().getNetherWorld();
        theEnd = plugin.getIslandWorldManager().getEndWorld();
    }

    /**
     * Function to check proximity to nether or end spawn location.
     * Used when playing with the standard nether or end.
     * 
     * @param location - the location
     * @return true if in the spawn area, false if not
     */
    private boolean awayFromSpawn(Location location) {
        Vector p = location.toVector().multiply(new Vector(1, 0, 1));
        Vector spawn = location.getWorld().getSpawnLocation().toVector().multiply(new Vector(1, 0, 1));
        return (spawn.distanceSquared(p) < (plugin.getSettings().getNetherSpawnRadius() * plugin.getSettings().getNetherSpawnRadius())) ? false : true;
    } 

    /**
     * If the player is in the standard nether or standard end or op, do nothing.
     * Used to protect the standard spawn for nether or end
     * @param player - the player
     * @return true if nothing needs to be done
     */
    private boolean noAction(Player player) {
        return (player.isOp()
                || (!player.getWorld().equals(nether) && !player.getWorld().equals(theEnd)) 
                || (player.getWorld().equals(nether) && plugin.getSettings().isNetherIslands()) 
                || (player.getWorld().equals(theEnd) && plugin.getSettings().isEndIslands())) ? true: false;
    }

    /**
     * Prevents blocks from being broken
     * 
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (noAction(e.getPlayer())) {
            return;
        }
        if (!awayFromSpawn(e.getBlock().getLocation())) {
            User.getInstance(e.getPlayer()).sendMessage(ERROR_NO_PERMISSION);
            e.setCancelled(true);
        }
    }

    /**
     * Protects standard nether or end spawn from bucket abuse
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (noAction(e.getPlayer())) {
            return;
        }
        if (!awayFromSpawn(e.getBlockClicked().getLocation())) {
            User.getInstance(e.getPlayer()).sendMessage(ERROR_NO_PERMISSION);
            e.setCancelled(true);
        }
    }

    /**
     * Handle end portals
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEndIslandPortal(PlayerPortalEvent e) {
        if (!e.getCause().equals(TeleportCause.END_PORTAL) || !plugin.getSettings().isEndGenerate()) {
            return;
        }
        if (!plugin.getIslandWorldManager().inWorld(e.getFrom())) {
            return;
        }
        // If entering a portal in the end, teleport home if you have one, else do nothing
        if (e.getFrom().getWorld().equals(theEnd)) {
            if (plugin.getIslands().hasIsland(e.getPlayer().getWorld(), e.getPlayer().getUniqueId())) {
                e.setCancelled(true);
                plugin.getIslands().homeTeleport(e.getPlayer().getWorld(), e.getPlayer());
            } 
            return;
        }
        // If this is island end, then go to the same location, otherwise try spawn
        Location to = plugin.getSettings().isEndIslands() ? e.getFrom().toVector().toLocation(theEnd) : theEnd.getSpawnLocation();
        // Else other worlds teleport to the end
        e.setCancelled(true);
        new SafeTeleportBuilder(plugin)
        .entity(e.getPlayer())
        .location(to)
        .build();
        return;
    }

    /**
     * This handles non-player portal use
     * Currently disables portal use by entities
     * 
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (plugin.getIslandWorldManager().inWorld(e.getFrom())) {
            // Disable entity portal transfer due to dupe glitching
            e.setCancelled(true);
        }
    }

    /**
     * Prevent standard nether or end spawns from being blown up
     * 
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public boolean onExplosion(EntityExplodeEvent e) {
        if (!plugin.getIslandWorldManager().inWorld(e.getLocation())) {
            return false;
        }
        if ((e.getLocation().getWorld().equals(nether) && plugin.getSettings().isNetherIslands())
                || (e.getLocation().getWorld().equals(theEnd) && plugin.getSettings().isEndIslands())) {
            // Not used in island worlds
            return false;
        }
        // Find out what is exploding
        Entity expl = e.getEntity();
        if (expl == null) {
            return false;
        }
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (!awayFromSpawn(b.getLocation())) {
                it.remove();
            }
        }
        return true;
    }

    /**
     * Handle nether portals
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public boolean onNetherPortal(PlayerPortalEvent e) {
        if (!e.getCause().equals(TeleportCause.NETHER_PORTAL) || !plugin.getIslandWorldManager().inWorld(e.getFrom())) {
            return false;
        }
        // If entering a portal in the nether, teleport to portal in overworld if there is one
        if (e.getFrom().getWorld().equals(nether)) {
            // If this is from the island nether, then go to the same vector, otherwise try island home location
            Location to = plugin.getSettings().isNetherIslands() ? e.getFrom().toVector().toLocation(world) : plugin.getIslands().getIslandLocation(e.getPlayer().getWorld(), e.getPlayer().getUniqueId());
            e.setCancelled(true);
            // Else other worlds teleport to the nether
            new SafeTeleportBuilder(plugin)
            .entity(e.getPlayer())
            .location(to)
            .portal()
            .build();
            return true;
        }
        // If this is island nether, then go to the same vector, otherwise try spawn
        Location to = plugin.getSettings().isNetherIslands() ? e.getFrom().toVector().toLocation(nether) : nether.getSpawnLocation();
        e.setCancelled(true);
        // Else other worlds teleport to the nether
        new SafeTeleportBuilder(plugin)
        .entity(e.getPlayer())
        .location(to)
        .portal()
        .build();
        return true;
    }

    /**
     * Prevents placing of blocks at standard nether or end spawns
     * 
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerBlockPlace(BlockPlaceEvent e) {
        if (noAction(e.getPlayer())) {
            return;
        }
        if (!awayFromSpawn(e.getBlock().getLocation())) {
            User.getInstance(e.getPlayer()).sendMessage(ERROR_NO_PERMISSION);
            e.setCancelled(true);
        }
    }

    /**
     * Converts trees to gravel and glowstone
     * 
     * @param e - event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public boolean onTreeGrow(StructureGrowEvent e) {
        if (!plugin.getSettings().isNetherTrees() || !e.getWorld().equals(nether)) {
            return false;
        }
        for (BlockState b : e.getBlocks()) {
            if (b.getType() == Material.LOG || b.getType() == Material.LOG_2) {
                b.setType(Material.GRAVEL);
            } else if (b.getType() == Material.LEAVES || b.getType() == Material.LEAVES_2) {
                b.setType(Material.GLOWSTONE);
            }
        }
        return true;
    }
}