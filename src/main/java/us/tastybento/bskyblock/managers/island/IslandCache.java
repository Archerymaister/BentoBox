package us.tastybento.bskyblock.managers.island;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import us.tastybento.bskyblock.database.objects.Island;

public class IslandCache {
    private BiMap<Location, Island> islandsByLocation;
    /**
     * Every player who is associated with an island is in this map.
     */
    private Map<World, Map<UUID, Island>> islandsByUUID;
    private Map<World, IslandGrid> grids;
    private IslandGrid defaultGrid;

    public IslandCache() {
        islandsByLocation = HashBiMap.create();
        islandsByUUID = new HashMap<>();
        grids = new HashMap<>();
        defaultGrid = new IslandGrid();
    }

    /**
     * Adds an island to the grid
     * @param island
     * @return true if successfully added, false if not
     */
    public boolean addIsland(Island island) {
        islandsByLocation.put(island.getCenter(), island);
        islandsByUUID.putIfAbsent(island.getWorld(), new HashMap<>());
        islandsByUUID.get(island.getWorld()).put(island.getOwner(), island);
        for (UUID member: island.getMemberSet()) {
            islandsByUUID.get(island.getWorld()).put(member, island);
        }
        return addToGrid(island);
    }

    /**
     * Adds a player's UUID to the look up for islands. Does no checking
     * @param uuid - player's uuid
     * @param island - island to associate with this uuid. Only one island can be associated per world.
     */
    public void addPlayer(UUID uuid, Island island) {
        islandsByUUID.putIfAbsent(island.getWorld(), new HashMap<>());
        islandsByUUID.get(island.getWorld()).put(uuid, island);
    }

    /**
     * Adds an island to the grid register
     * @param newIsland
     * @return true if successfully added, false if not
     */
    private boolean addToGrid(Island newIsland) {
        return grids.getOrDefault(newIsland.getWorld(), defaultGrid).addToGrid(newIsland);
    }

    public void clear() {
        islandsByLocation.clear();
        islandsByUUID.clear();
    }

    /**
     * Deletes an island from the database. Does not remove blocks
     * @param island
     * @return true if successful, false if not
     */
    public boolean deleteIslandFromCache(Island island) {
        if (!islandsByLocation.remove(island.getCenter(), island) || !islandsByUUID.containsKey(island.getWorld())) {         
            return false;
        }
        Iterator<Entry<UUID, Island>> it = islandsByUUID.get(island.getWorld()).entrySet().iterator();
        while (it.hasNext()) {
            Entry<UUID, Island> en = it.next();
            if (en.getValue().equals(island)) {
                it.remove();
            }
        }
        // Remove from grid
        return grids.getOrDefault(island.getWorld(), defaultGrid).removeFromGrid(island);
    }

    /**
     * Get island based on the exact center location of the island
     * @param location
     * @return island or null if it does not exist
     */
    public Island get(Location location) {
        return islandsByLocation.get(location);
    }

    /**
     * Returns island referenced by UUID
     * @param world - world to check
     * @param uuid - player
     * @return island or null if none
     */
    public Island get(World world, UUID uuid) {
        return islandsByUUID.containsKey(world) ? islandsByUUID.get(world).get(uuid) : null;
    }

    /**
     * Returns the island at the location or null if there is none.
     * This includes the full island space, not just the protected area
     *
     * @param location - the location
     * @return Island object
     */
    public Island getIslandAt(Location location) {
        if (location == null) {
            return null;
        }
        return grids.getOrDefault(location.getWorld(), defaultGrid).getIslandAt(location.getBlockX(), location.getBlockZ());
    }
    
    public Collection<Island> getIslands() {
        return Collections.unmodifiableCollection(islandsByLocation.values());
    }

    /**
     * @param world - world to check
     * @param uuid - uuid of player to check
     * @return set of UUID's of island members. If there is no island, this set will be empty
     */
    public Set<UUID> getMembers(World world, UUID uuid) {
        islandsByUUID.putIfAbsent(world, new HashMap<>());
        Island island = islandsByUUID.get(world).get(uuid);
        if (island != null) {
            return island.getMemberSet();
        }
        return new HashSet<>(0);
    }

    /**
     * @param world - the world to check
     * @param uuid - player's uuid
     * @return team leader's UUID, the player UUID if they are not in a team, or null if there is no island
     */
    public UUID getTeamLeader(World world, UUID uuid) {
        islandsByUUID.putIfAbsent(world, new HashMap<>());
        Island island = islandsByUUID.get(world).get(uuid);
        if (island != null) {
            return island.getOwner();
        }
        return null;
    }

    /**
     * @param world - the world to check
     * @param uuid - the player
     * @return true if player has island and owns it
     */
    public boolean hasIsland(World world, UUID uuid) {
        islandsByUUID.putIfAbsent(world, new HashMap<>());
        Island island = islandsByUUID.get(world).get(uuid);
        if (island != null) {
            return island.getOwner().equals(uuid);
        }
        return false;
    }

    /**
     * Removes a player from the cache. If the player has an island, the island owner is removed and membership cleared.
     * The island is removed from the islandsByUUID map, but kept in the location map.
     * @param playerUUID - player's UUID
     */
    public void removePlayer(World world, UUID uuid) {
        islandsByUUID.putIfAbsent(world, new HashMap<>());
        Island island = islandsByUUID.get(world).get(uuid);
        if (island != null) {
            if (island.getOwner() != null && island.getOwner().equals(uuid)) {
                // Clear ownership and members
                island.getMembers().clear();
                island.setOwner(null);
            } else {
                // Remove player from the island membership
                island.removeMember(uuid);
            }
        }
        islandsByUUID.get(world).remove(uuid);
    }

    /**
     * Get the number of islands in the cache
     * @return the number of islands 
     */
    public int size() {
        return islandsByLocation.size();
    }

    /**
     * Sets an island owner. Clears out any other owner
     * @param island - island
     * @param newOwnerUUID - new owner
     */
    public void setOwner(Island island, UUID newOwnerUUID) {
        island.setOwner(newOwnerUUID);
        islandsByUUID.putIfAbsent(island.getWorld(), new HashMap<>());
        islandsByUUID.get(island.getWorld()).put(newOwnerUUID, island);
        islandsByLocation.put(island.getCenter(), island);        
    }
    
}
