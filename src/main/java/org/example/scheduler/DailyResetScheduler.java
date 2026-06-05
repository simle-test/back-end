package org.example.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DailyResetScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyResetScheduler.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyData() {
        try {
            String sql = "UPDATE \"user_data\" SET \"TodayNumber\" = 0, \"TodayCorrect\" = 0, \"TodayCorrectRate\" = 0";
            int updatedRows = jdbcTemplate.update(sql);
            
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logger.info("每日数据重置完成，时间：{}，重置用户数：{}", time, updatedRows);
        } catch (Exception e) {
            logger.error("每日数据重置失败", e);
        }
    }
}