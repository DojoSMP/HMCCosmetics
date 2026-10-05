package com.hibiscusmc.hmccosmetics.database.types;

import java.util.concurrent.CompletionException;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class SQLData extends Data {
    // ponytail: one worker per SQL connection; use a connection pool if database throughput requires it.
    protected final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HMCCosmetics-database");
        thread.setDaemon(true);
        return thread;
    });
    @Override
    @SuppressWarnings({"resource"}) // Duplicate is from deprecated InternalData
    public CompletableFuture<UserData> get(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            UserData data = new UserData(uniqueId);

            try (PreparedStatement preparedStatement = preparedStatement("SELECT * FROM COSMETICDATABASE WHERE UUID = ?;")){
                preparedStatement.setString(1, uniqueId.toString());
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        String rawData = rs.getString("COSMETICS");
                        HashMap<CosmeticSlot, Map.Entry<Cosmetic, Integer>> cosmetics = deserializeData(rawData);
                        data.setCosmetics(cosmetics);
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException("Unable to load cosmetics for " + uniqueId, e);
            }
            return data;
        }, worker);
    }

    @Override
    @SuppressWarnings("resource")
    public void save(CosmeticUser user) {
        if (user == null) return;
        String serialized = serializeData(user);
        UUID uuid = user.getUniqueId();
        Runnable run = () -> {
            try (PreparedStatement preparedSt = preparedStatement("REPLACE INTO COSMETICDATABASE(UUID,COSMETICS) VALUES(?,?);")) {
                preparedSt.setString(1, uuid.toString());
                preparedSt.setString(2, serialized);
                preparedSt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };
        worker.execute(run);
    }

    @Override
    public void shutdown() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Database writes are still pending; connection kept open to avoid losing data");
            }
            closeConnection();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for database writes", e);
        }
    }

    protected abstract void closeConnection();

    public abstract PreparedStatement preparedStatement(String query);
}
