package org.example.revolverui.web;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.example.revolverui.config.AppConfigService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final JdbcTemplate jdbc;
    private final AppConfigService config;

    public StatsController(JdbcTemplate jdbc, AppConfigService config) {
        this.jdbc = jdbc;
        this.config = config;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM assignment_stats", Long.class);
        List<Map<String, Object>> today = jdbc.queryForList(
                "SELECT employee_login, COUNT(*) AS cnt FROM assignment_stats " +
                        "WHERE assigned_at::date = CURRENT_DATE " +
                        "GROUP BY employee_login ORDER BY cnt DESC");
        Map<String, Object> result = new HashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("today", today);
        return result;
    }

    @GetMapping("/daily")
    public List<Map<String, Object>> daily(@RequestParam(defaultValue = "7") int days) {
        return jdbc.queryForList(
                "SELECT assigned_at::date AS day, employee_login, COUNT(*) AS cnt " +
                        "FROM assignment_stats WHERE assigned_at >= CURRENT_DATE - make_interval(days => ?) " +
                        "GROUP BY 1, 2 ORDER BY day DESC, cnt DESC", days);
    }

    /** Собирает WHERE-часть для фильтров истории, чтобы не дублировать код в выборке и экспорте. */
    private String buildWhere(String from, String to, String login, String group, List<Object> args) {
        StringBuilder sb = new StringBuilder(
                "WHERE assigned_at >= ?::date AND assigned_at < ?::date + interval '1 day'");
        args.add(from);
        args.add(to);
        if (login != null && !login.isEmpty()) {
            sb.append(" AND employee_login = ?");
            args.add(login);
        }
        if (group != null && !group.isEmpty()) {
            sb.append(" AND task_group = ?");
            args.add(group);
        }
        return sb.toString();
    }

    @GetMapping("/history")
    public Map<String, Object> history(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String login,
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Object> args = new ArrayList<>();
        String where = buildWhere(from, to, login, group, args);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assignment_stats " + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page - 1) * size);
        pageArgs.add(size);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT to_char(assigned_at, 'DD-MM-YYYY HH24:MI:SS') AS assigned_at, " +
                        "task_id, employee_login, executor_id, task_group " +
                        "FROM assignment_stats " + where +
                        " ORDER BY assigned_at DESC OFFSET ? LIMIT ?", pageArgs.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("rows", rows);
        return result;
    }

    @GetMapping("/history/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String login,
            @RequestParam(required = false) String group) {

        List<Object> args = new ArrayList<>();
        String where = buildWhere(from, to, login, group, args);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT to_char(assigned_at, 'DD-MM-YYYY HH24:MI:SS') AS assigned_at, " +
                        "task_id, employee_login, executor_id " +
                        "FROM assignment_stats " + where +
                        " ORDER BY assigned_at DESC", args.toArray());

        String[] headers = {"Время", "Задача", "Сотрудник", "ExecutorId"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("История");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (Map<String, Object> row : rows) {
                Row line = sheet.createRow(r++);
                line.createCell(0).setCellValue(String.valueOf(row.get("assigned_at")));
                line.createCell(1).setCellValue(row.get("task_id") == null ? "" : String.valueOf(row.get("task_id")));
                line.createCell(2).setCellValue(row.get("employee_login") == null ? "" : String.valueOf(row.get("employee_login")));
                line.createCell(3).setCellValue(row.get("executor_id") == null ? "" : String.valueOf(row.get("executor_id")));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);

            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=history_" + from + "_" + to + "_" + stamp + ".xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/employees")
    public List<String> employees() {
        List<String> logins = new ArrayList<>(config.getExecutorMap().keySet());
        logins.sort(String.CASE_INSENSITIVE_ORDER);
        return logins;
    }
}