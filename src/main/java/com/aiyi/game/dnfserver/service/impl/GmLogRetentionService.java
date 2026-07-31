package com.aiyi.game.dnfserver.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/** Keeps append-only GM audit tables from growing without bound. */
@Service
public class GmLogRetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GmLogRetentionService.class);
    private static final int RETENTION_DAYS = 7;
    private static final int DELETE_BATCH_SIZE = 5000;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 30 3 * * ?", zone = "Asia/Shanghai")
    public void cleanupExpiredLogs() {
        int deleted = 0;
        deleted += cleanupTable("gm_online_reward_log", "awarded_at", null);
        deleted += cleanupTable("gm_cdk_redemption", "redeemed_at", null);
        deleted += cleanupTable("gm_backup_entry", "created_at", "status='FAILED'");
        deleted += cleanupTable("d_guild", "guild_grade_log", "occ_time", null);
        deleted += cleanupTable("gm_operation_audit", "created_at", null);
        if (deleted > 0) {
            LOGGER.info("Deleted {} GM log rows older than {} days", deleted, RETENTION_DAYS);
        }
    }

    private int cleanupTable(String table, String timeColumn, String predicate) {
        return cleanupTable("dnf_service", table, timeColumn, predicate);
    }

    private int cleanupTable(String schema, String table, String timeColumn, String predicate) {
        StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(schema)
                .append(".")
                .append(table)
                .append(" WHERE ");
        if (predicate != null) {
            sql.append(predicate).append(" AND ");
        }
        sql.append(timeColumn)
                .append(" < DATE_SUB(NOW(), INTERVAL ")
                .append(RETENTION_DAYS)
                .append(" DAY) LIMIT ")
                .append(DELETE_BATCH_SIZE);

        int total = 0;
        try {
            int batch;
            do {
                batch = jdbcTemplate.update(sql.toString());
                total += batch;
            } while (batch == DELETE_BATCH_SIZE);
        } catch (Exception e) {
            LOGGER.warn("GM log retention skipped {}: {}", table, e.getMessage());
        }
        return total;
    }
}
