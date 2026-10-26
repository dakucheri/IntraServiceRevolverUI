package org.example.revolverui.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.example.revolverui.config.AppConfigService;
import org.example.revolverui.db.StatsDao;
import org.example.revolverui.stats.StatsFileWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class AssignerService {

    @Value("${app.auth}")
    private String auth;
    @Value("${app.base-url}")
    private String baseUrl;
    @Value("${app.active-line-url}")
    private String activeLineUrl;
    @Value("${app.observer-id}")
    private String observerId;

    private final AppConfigService config;
    private final StatsDao statsDao;
    private final StatsFileWriter fileWriter;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<Integer> processedTaskIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger currentEmployeeIndex = new AtomicInteger(0);
    private volatile List<String> currentActiveEmployees = Collections.emptyList();
    private final Map<LocalDate, Map<String, Integer>> dailyStats = new ConcurrentHashMap<>();
    private final Object statsLock = new Object();
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    public AssignerService(AppConfigService config, StatsDao statsDao, StatsFileWriter fileWriter) {
        this.config = config;
        this.statsDao = statsDao;
        this.fileWriter = fileWriter;
    }

    @PostConstruct
    public void start() {
        running.set(true);
        Thread t = new Thread(this::processTasks, "TaskProcessor");
        t.setDaemon(false);
        t.start();
        log("INFO", "Processing started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
    }

    private CloseableHttpClient createHttpClient() {
        try {
            TrustStrategy trustAll = (certs, authType) -> true;
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, trustAll).build();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(15000)
                    .setSocketTimeout(30000)
                    .build();
            return HttpClients.custom().setSSLSocketFactory(sslsf).setDefaultRequestConfig(requestConfig).build();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании HttpClient", e);
        }
    }

    private String now() {
        return LocalDateTime.now(ZoneId.of("Europe/Moscow"))
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    private void log(String level, String message) {
        switch (level) {
            case "ERROR" -> logger.error(message);
            case "WARNING" -> logger.warn(message);
            default -> logger.info(message);
        }
    }

    private void processTasks() {
        try (CloseableHttpClient client = createHttpClient()) {
            while (running.get()) {
                // Конфиг читаем из БД на каждом цикле — правки через веб применяются без рестарта
                int intervalMs = config.getInt(AppConfigService.CHECK_INTERVAL_MS, 60000);
                if ("false".equalsIgnoreCase(config.get(AppConfigService.ENABLED).orElse("true"))) {
                    log("INFO", "Обработка отключена (APP_ENABLED=false) — спим и ждём");
                    Thread.sleep(intervalMs);
                    continue;
                }
                Map<String, String> executorMap = config.getExecutorMap();

                List<String> activeEmployees = getActiveEmployees(client, executorMap);
                log("INFO", "Активных сотрудников: " + activeEmployees.size() + " -> " + activeEmployees);

                if (!activeEmployees.isEmpty()) {
                    processTasksGroup1(client, activeEmployees, executorMap);
                    processTasksGroup2(client, activeEmployees, executorMap);
                } else {
                    log("WARNING", "Нет активных сотрудников — пропускаем итерацию");
                }

                Thread.sleep(intervalMs);
            }
        } catch (Exception e) {
            System.err.println("Критическая ошибка в процессе обработки: " + e);
        }
    }

    private void processTasksGroup1(CloseableHttpClient client, List<String> activeEmployees, Map<String, String> executorMap) {
        String requestUrl = baseUrl + "?StatusIds=43,31&ServiceIds=64,63,62,61,50,49,46,48,45,42,41,44,43,32,22,21,20,17&fields=Id,Name,StatusId,Creator,ExecutorIds,ServiceId,ObserverIds&PageSize=50";
        HttpGet getRequest = new HttpGet(requestUrl);
        getRequest.setHeader("Authorization", auth);

        List<Task> tasks = fetchTasks(client, getRequest);
        List<Task> unprocessed = tasks.stream()
                .filter(t -> !processedTaskIds.contains(t.getId()))
                .sorted(Comparator.comparingInt(Task::getId))
                .collect(Collectors.toList());
        if (unprocessed.isEmpty()) {
            log("INFO", "Group1: задач не найдено");
            return;
        }
        log("INFO", "Group1: новых задач: " + unprocessed.size());
        processTasksInParallel(unprocessed, activeEmployees, executorMap, client, false);
    }

    private void processTasksGroup2(CloseableHttpClient client, List<String> activeEmployees, Map<String, String> executorMap) {
        try {
            List<Task> stableTasks = getStableTasksForGroup2(client);
            List<Task> unprocessed = stableTasks.stream()
                    .filter(t -> !processedTaskIds.contains(t.getId()))
                    .sorted(Comparator.comparingInt(Task::getId))
                    .collect(Collectors.toList());
            if (unprocessed.isEmpty()) {
                log("INFO", "Group2: нет новых стабильных задач");
                return;
            }
            log("INFO", "Group2: новых стабильных задач: " + unprocessed.size());
            processTasksInParallel(unprocessed, activeEmployees, executorMap, client, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Task> getStableTasksForGroup2(CloseableHttpClient client) throws InterruptedException {
        String requestUrl = baseUrl + "?ServiceIds=47,19,65&StatusIds=27&fields=Id,Name,StatusId,Creator,ExecutorIds,ServiceId,ObserverIds&PageSize=50";

        HttpGet get1 = new HttpGet(requestUrl);
        get1.setHeader("Authorization", auth);
        List<Task> firstBatch = fetchTasks(client, get1);
        if (firstBatch.isEmpty()) return Collections.emptyList();

        Thread.sleep(5000);

        HttpGet get2 = new HttpGet(requestUrl);
        get2.setHeader("Authorization", auth);
        List<Task> secondBatch = fetchTasks(client, get2);
        if (secondBatch.isEmpty()) return Collections.emptyList();

        Set<Integer> firstIds = firstBatch.stream().map(Task::getId).collect(Collectors.toSet());
        return secondBatch.stream().filter(t -> firstIds.contains(t.getId())).collect(Collectors.toList());
    }

    private List<Task> fetchTasks(CloseableHttpClient client, HttpGet getRequest) {
        try {
            HttpResponse response = client.execute(getRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");
            if (statusCode == 200) {
                TaskList wrapper = mapper.readValue(body, TaskList.class);
                return wrapper.getTasks();
            }
            log("ERROR", "Ошибка HTTP: код " + statusCode);
            return Collections.emptyList();
        } catch (Exception e) {
            log("ERROR", "Ошибка получения списка задач: " + e);
            return Collections.emptyList();
        }
    }

    private void processTasksInParallel(List<Task> tasks, List<String> activeEmployees,
                                        Map<String, String> executorMap, CloseableHttpClient client, boolean isGroup2) {
        int parallelism = config.getInt(AppConfigService.PARALLELISM, 2);
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<Future<?>> futures = new ArrayList<>();

        for (Task task : tasks) {
            if (!running.get()) break;
            futures.add(executor.submit(() -> processTask(client, task, activeEmployees, executorMap, isGroup2)));
        }

        for (Future<?> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log("ERROR", "Ошибка ожидания задачи: " + e);
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private List<String> getActiveEmployees(CloseableHttpClient client, Map<String, String> executorMap) {
        try {
            HttpGet getRequest = new HttpGet(activeLineUrl);
            HttpResponse response = client.execute(getRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");
            if (statusCode == 200) {
                Employee[] employees = mapper.readValue(body, Employee[].class);
                List<String> result = new ArrayList<>();
                for (Employee e : employees) {
                    if (executorMap.containsKey(e.getDisplayName())) result.add(e.getDisplayName());
                }
                synchronized (this) {
                    if (!currentActiveEmployees.equals(result)) {
                        currentActiveEmployees = result;
                        currentEmployeeIndex.set(0);
                        log("INFO", "Обновлён список активных сотрудников: " + currentActiveEmployees);
                    }
                }
                return result;
            }
            log("ERROR", "Ошибка получения активных сотрудников: код " + statusCode);
        } catch (Exception e) {
            log("ERROR", "Исключение при получении активных сотрудников: " + e);
        }
        return Collections.emptyList();
    }

    private void processTask(CloseableHttpClient client, Task task, List<String> activeEmployees,
                             Map<String, String> executorMap, boolean isGroup2) {
        int taskId = task.getId();

        if (processedTaskIds.contains(taskId)) return;

        if (task.getExecutorIds() != null && !task.getExecutorIds().isEmpty()) {
            processedTaskIds.add(taskId);
            return;
        }

        if (activeEmployees.isEmpty()) return;

        int index = currentEmployeeIndex.getAndIncrement();
        String login = activeEmployees.get(index % activeEmployees.size());
        String executorId = executorMap.get(login);
        if (executorId == null) {
            log("WARNING", "Для сотрудника " + login + " не найден ExecutorId");
            return;
        }

        try {
            updateTask(client, task, executorId, isGroup2);

            // Дневная статистика и CSV — как раньше
            dailyStats.computeIfAbsent(LocalDate.now(), d -> new ConcurrentHashMap<>())
                    .merge(login, 1, Integer::sum);
            synchronized (statsLock) {
                fileWriter.append(taskId, now(), login, executorId);
                fileWriter.writeSummary(processedTaskIds.size(), dailyStats, now());
            }

            // БД — если упало, CSV всё равно жив, продолжаем работу
            try {
                statsDao.insert(taskId, login, executorId, isGroup2 ? "GROUP2" : "GROUP1");
            } catch (Exception e) {
                log("ERROR", "Не удалось записать в БД по задаче " + taskId + ": " + e);
            }

            processedTaskIds.add(taskId);
            log("INFO", "Задача №" + taskId + " назначена на " + login + " (ID: " + executorId + ")");
        } catch (Exception e) {
            log("ERROR", "Ошибка обновления задачи №" + taskId + ": " + e);
        }
    }

    private void updateTask(HttpClient client, Task task, String executorId, boolean isGroup2) throws IOException {
        HttpPut putRequest = new HttpPut(baseUrl + "/" + task.getId());
        putRequest.setHeader("Authorization", auth);
        putRequest.setHeader("Content-Type", "application/json");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ExecutorIds", executorId);

        // Берём ObserverIds из задачи; если пусто — используем дефолт из конфига
        String observerIds = task.getObserverIds();
        if (observerIds == null || observerIds.isBlank()) {
            observerIds = observerId; // observerId — это @Value("${app.observer-id}")
        }
        requestBody.put("ObserverIds", observerIds);

        if (!isGroup2) {
            requestBody.put("StatusId", "27");
            requestBody.put("Comment", "Здравствуйте. Информируем о том, что Ваша заявка в работе.");
            requestBody.put("IsPrivateComment", false);
        }

        String jsonBody = mapper.writeValueAsString(requestBody);
        log("DEBUG", "PUT body для задачи №" + task.getId() + ": " + jsonBody);

        putRequest.setEntity(new StringEntity(jsonBody, "UTF-8"));

        HttpResponse response = client.execute(putRequest);
        int statusCode = response.getStatusLine().getStatusCode();
        String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

        if (statusCode != 200 && statusCode != 204) {
            throw new IOException("HTTP " + statusCode + " при обновлении задачи №" + task.getId() + ": " + responseBody);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Employee {
        @JsonProperty("display_name")
        private String displayName;
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Task {
        @JsonProperty("Id") private int id;
        @JsonProperty("Name") private String name;
        @JsonProperty("StatusId") private int statusId;
        @JsonProperty("Creator") private String creator;
        @JsonProperty("ExecutorIds") private String executorIds;
        @JsonProperty("ServiceId") private Integer serviceId;
        @JsonProperty("ObserverIds") private String observerIds; // <-- новое поле

        public int getId() { return id; }
        public String getExecutorIds() { return executorIds; }
        public String getObserverIds() { return observerIds; } // <-- новый геттер
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskList {
        @JsonProperty("Tasks") private List<Task> tasks;
        public List<Task> getTasks() { return tasks != null ? tasks : Collections.emptyList(); }
    }
}