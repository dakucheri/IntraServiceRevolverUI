package org.example.revolverui.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatsDao {
    private final JdbcTemplate jdbc;

    public StatsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(int taskId, String login, String executorId, String group) {
        jdbc.update("""
                INSERT INTO assignment_stats (task_id, employee_login, executor_id, task_group)
                VALUES (?, ?, ?, ?)
                """, taskId, login, executorId, group);
    }

    public long total() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM assignment_stats", Long.class);
        return n == null ? 0 : n;
    }
}