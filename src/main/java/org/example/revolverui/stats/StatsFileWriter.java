package org.example.revolverui.stats;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;

@Component
public class StatsFileWriter {
    @Value("${app.stats-file}")
    private String statsFilePath;
    @Value("${app.summary-file}")
    private String summaryFilePath;

    @PostConstruct
    public void init() {
        try {
            File f = new File(statsFilePath);
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
                    bw.write("TaskId,Timestamp,EmployeeLogin,ExecutorId\n");
                }
            }
            File summary = new File(summaryFilePath);
            if (!summary.exists()) {
                summary.getParentFile().mkdirs();
                summary.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Ошибка инициализации файлов статистики: " + e.getMessage());
        }
    }

    public void append(int taskId, String timestamp, String login, String executorId) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(statsFilePath, true))) {
            bw.write(String.format("%d,%s,%s,%s%n", taskId, timestamp, login, executorId));
        } catch (IOException e) {
            System.err.println("Ошибка записи статистики в CSV: " + e.getMessage());
        }
    }

    public void writeSummary(long processedTotal, Map<LocalDate, Map<String, Integer>> dailyStats, String now) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Сводная статистика ===\n");
            sb.append("Всего обработано задач: ").append(processedTotal).append("\n");
            Map<String, Integer> today = dailyStats.get(LocalDate.now());
            if (today != null && !today.isEmpty()) {
                sb.append("Статистика по сотрудникам за сегодня:\n");
                today.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append(" задач\n"));
            } else {
                sb.append("Сегодня задач не назначалось или статистика пуста.\n");
            }
            sb.append("Время последнего обновления: ").append(now).append("\n");
            Files.write(new File(summaryFilePath).toPath(), sb.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Ошибка записи сводной статистики: " + e.getMessage());
        }
    }
}