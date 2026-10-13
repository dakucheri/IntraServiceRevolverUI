package org.example.revolverui.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LogBuffer extends AppenderBase<ILoggingEvent> {

    private static final int MAX_LINES = 5000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final Deque<String> lines = new ArrayDeque<>();
    private final AtomicLong seq = new AtomicLong();

    @PostConstruct
    public void register() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(ctx);
        setName("memoryBuffer");
        start();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        // время показываем по МСК независимо от таймзоны машины
        String time = LocalDateTime.ofInstant(event.getInstant(), ZoneId.of("Europe/Moscow")).format(FMT);
        String line = "[" + time + "] [" + event.getThreadName() + "] [" + event.getLevel() + "] "
                + event.getFormattedMessage();
        String entry = seq.incrementAndGet() + "\t" + line;
        synchronized (lines) {
            if (lines.size() >= MAX_LINES) lines.pollFirst();
            lines.addLast(entry);
        }
    }

    /** Возвращает строки с id больше sinceId в формате "id\tтекст". */
    public List<String> getLogs(long sinceId) {
        synchronized (lines) {
            long maxId = seq.get();
            // после перезапуска приложения счётчик начинается с нуля — сбрасываем клиента на начало
            long from = sinceId > maxId ? 0 : sinceId;
            List<String> result = new ArrayList<>();
            for (String line : lines) {
                long id = Long.parseLong(line.substring(0, line.indexOf('\t')));
                if (id > from) result.add(line);
            }
            return result;
        }
    }
}