package me.william278.crossserversync.bungeecord.data;

import me.william278.crossserversync.PlayerData;
import me.william278.crossserversync.bungeecord.CrossServerSyncBungeeCord;
import me.william278.crossserversync.bungeecord.data.sql.Database;

import java.sql.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;

public class DataManager {

    private static final CrossServerSyncBungeeCord plugin = CrossServerSyncBungeeCord.getInstance();
    public static PlayerDataCache playerDataCache;

    public static void setupCache() {
        playerDataCache = new PlayerDataCache();
    }

    /**
     * Checks if the player is registered on the database; register them if not.
     *
     * @param playerUUID The UUID of the player to register
     */
    public static void ensurePlayerExists(UUID playerUUID) {
        if (!playerExists(playerUUID)) {
            createPlayerEntry(playerUUID);
        }
    }

    /**
     * Returns whether the player is registered in SQL (an entry in the PLAYER_TABLE)
     *
     * @param playerUUID The UUID of the player
     * @return {@code true} if the player is on the player table
     */
    private static boolean playerExists(UUID playerUUID) {
        try (Connection connection = CrossServerSyncBungeeCord.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM " + Database.PLAYER_TABLE_NAME + " WHERE `uuid`=?;")) {
                statement.setString(1, playerUUID.toString());
                ResultSet resultSet = statement.executeQuery();
                return resultSet.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception occurred", e);
            return false;
        }
    }

    private static void createPlayerEntry(UUID playerUUID) {
        try (Connection connection = CrossServerSyncBungeeCord.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + Database.PLAYER_TABLE_NAME + " (`uuid`) VALUES(?);")) {
                statement.setString(1, playerUUID.toString());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception occurred", e);
        }
    }

    public static PlayerData getPlayerData(UUID playerUUID) {
        try (Connection connection = CrossServerSyncBungeeCord.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM " + Database.DATA_TABLE_NAME + " WHERE `player_id`=(SELECT `id` FROM " + Database.PLAYER_TABLE_NAME + " WHERE `uuid`=?);")) {
                statement.setString(1, playerUUID.toString());
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    final UUID dataVersionUUID = UUID.fromString(resultSet.getString("version_uuid"));
                    final Timestamp dataSaveTimestamp = resultSet.getTimestamp("timestamp");
                    final String serializedInventory = resultSet.getString("inventory");
                    final String serializedEnderChest = resultSet.getString("ender_chest");
                    final double health = resultSet.getDouble("health");
                    final double maxHealth = resultSet.getDouble("max_health");
                    final double hunger = resultSet.getDouble("hunger");
                    final double saturation = resultSet.getDouble("saturation");
                    final String serializedStatusEffects = resultSet.getString("status_effects");

                    return new PlayerData(playerUUID, dataVersionUUID, serializedInventory, serializedEnderChest, health, maxHealth, hunger, saturation, serializedStatusEffects);
                } else {
                    return PlayerData.EMPTY_PLAYER_DATA(playerUUID);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception occurred", e);
            return null;
        }
    }

    public static void updatePlayerData(PlayerData playerData, UUID lastDataUUID) {
        // Ignore if the Spigot server didn't properly sync the previous data
        PlayerData oldPlayerData = playerDataCache.getPlayer(playerData.getPlayerUUID());
        if (oldPlayerData != null) {
            if (oldPlayerData.getDataVersionUUID() != lastDataUUID) {
                return;
            }
        }

        // Add the new player data to the cache
        playerDataCache.updatePlayer(playerData);

        // SQL: If the player has cached data, update it, otherwise insert new data.
        if (playerHasCachedData(playerData.getPlayerUUID())) {
            updatePlayerData(playerData);
        } else {
            insertPlayerData(playerData);
        }
    }

    private static void updatePlayerData(PlayerData playerData) {
        try (Connection connection = CrossServerSyncBungeeCord.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + Database.DATA_TABLE_NAME + " SET `version_uuid`=?, `timestamp`=?, `inventory`=?, `ender_chest`=?, `health`=?, `max_health`=?, `hunger`=?, `saturation`=?, `status_effects`=? WHERE `player_id`=(SELECT `id` FROM " + Database.PLAYER_TABLE_NAME + " WHERE `uuid`=?);")) {
                statement.setString(1, playerData.getDataVersionUUID().toString());
                statement.setTimestamp(2, new Timestamp(Instant.now().getEpochSecond()));
                statement.setString(3, playerData.getSerializedInventory());
                statement.setString(4, playerData.getSerializedEnderChest());
                statement.setDouble(5, 20D); // Health
                statement.setDouble(6, 20D); // Max health
                statement.setDouble(7, 20D); // Hunger
                statement.setDouble(8, 20D); // Saturation
                statement.setString(9, ""); // Status effects

                statement.setString(10, playerData.getPlayerUUID().toString());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception occurred", e);
        }
    }

    private static void insertPlayerData(PlayerData playerData) {
        try (Connection connection = CrossServerSyncBungeeCord.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + Database.DATA_TABLE_NAME + " (`player_id`,`version_uuid`,`timestamp`,`inventory`,`ender_chest`,`health`,`max_health`,`hunger`,`saturation`,`status_effects`) VALUES((SELECT `id` FROM " + Database.PLAYER_TABLE_NAME + " WHERE `uuid`=?),?,?,?,?,?,?,?,?,?);")) {
                statement.setString(1, playerData.getPlayerUUID().toString());
                statement.setString(2, playerData.getDataVersionUUID().toString());
                statement.setTimestamp(3, new Timestamp(Instant.now().getEpochSecond()));
                statement.setString(4, playerData.getSerializedInventory());
                statement.setString(5, playerData.getSerializedEnderChest());
                statement.setDouble(6, 20D); // Health
                statement.setDouble(7, 20D); // Max health
                statement.setDouble(8, 20D); // Hunger
                statement.setDouble(9, 20D); // Saturation
                statement.setString(10, ""); // Status effects

                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception occurred", e);
        }
    }

    /**
     * Returns whether the player has cached data saved in SQL (an entry in the DATA_TABLE)
     *
     * @param playerUUID The UUID of the player
     * @return {@code true} if the player has an entry in the data table
     */
    private static boolean playerHasCachedData(UUID playerUUID) {
        try (Connection connection = CrossServerSyncBungeeCord.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM " + Database.DATA_TABLE_NAME + " WHERE `player_id`=(SELECT `id` FROM " + Database.PLAYER_TABLE_NAME + " WHERE `uuid`=?);")) {
                statement.setString(1, playerUUID.toString());
                ResultSet resultSet = statement.executeQuery();
                return resultSet.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "An SQL exception occurred", e);
            return false;
        }
    }

    /**
     * A cache of PlayerData
     */
    public static class PlayerDataCache {

        // The cached player data
        public HashSet<PlayerData> playerData;

        public PlayerDataCache() {
            playerData = new HashSet<>();
        }

        /**
         * Update ar add data for a player to the cache
         *
         * @param newData The player's new/updated {@link PlayerData}
         */
        public void updatePlayer(PlayerData newData) {
            // Remove the old data if it exists
            PlayerData oldData = null;
            for (PlayerData data : playerData) {
                if (data.getPlayerUUID() == newData.getPlayerUUID()) {
                    oldData = data;
                }
            }
            if (oldData != null) {
                playerData.remove(oldData);
            }

            // Add the new data
            playerData.add(newData);
        }

        /**
         * Get a player's {@link PlayerData} by their {@link UUID}
         *
         * @param playerUUID The {@link UUID} of the player to check
         * @return The player's {@link PlayerData}
         */
        public PlayerData getPlayer(UUID playerUUID) {
            for (PlayerData data : playerData) {
                if (data.getPlayerUUID() == playerUUID) {
                    return data;
                }
            }
            return null;
        }

        /**
         * Remove a player's {@link PlayerData} from the cache
         * @param playerUUID The UUID of the player to remove
         */
        public void removePlayer(UUID playerUUID) {
            PlayerData dataToRemove = null;
            for (PlayerData data : playerData) {
                if (data.getPlayerUUID() == playerUUID) {
                    dataToRemove = data;
                    break;
                }
            }
            if (dataToRemove != null) {
                playerData.remove(dataToRemove);
            }
        }

    }
}