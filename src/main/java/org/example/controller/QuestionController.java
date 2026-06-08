package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.entity.User;
import org.example.Question;
import org.example.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${wechat.appid}")
    private String wechatAppId;

    @Value("${wechat.secret}")
    private String wechatSecret;

    // type 转换表：前端英文 key -> 数据库中文
    private static final Map<String, String> TYPE_MAP = new HashMap<>();
    static {
        TYPE_MAP.put("language_understanding", "言语理解");
        TYPE_MAP.put("quantity_relation", "数量关系");
        TYPE_MAP.put("logical_judgment", "判断推理");
        TYPE_MAP.put("material_analysis", "资料分析");
        TYPE_MAP.put("common_sense_judgment", "常识判断");
        TYPE_MAP.put("political_theory", "政治理论");
    }

    // ====================== 题目接口 ======================
    @PostMapping("/addQuestion")
    public Result<String> addQuestion(@RequestBody Question question) {
        try {
            questionService.addQuestion(question);
            return Result.success("题目添加成功");
        } catch (Exception e) {
            return Result.error("添加失败");
        }
    }

    @PostMapping("/delete")
    public Result<String> deleteQuestion(@RequestBody Question question) {
        try {
            questionService.deleteQuestionById(question.getQuestion_id());
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败");
        }
    }

    @PatchMapping("/update")
    public Result<String> updateQuestion(@RequestBody Question question) {
        try {
            questionService.updateQuestion(question);
            return Result.success("修改成功");
        } catch (Exception e) {
            return Result.error("修改失败");
        }
    }

    @PutMapping("/submit")
    public Result<String> submitAnswer(@RequestBody Map<String, Object> body) {
        Integer questionId = (Integer) body.get("question_id");
        String selectedAnswer = (String) body.get("selected_answer");
        Integer dataId = (Integer) body.get("data_id");
        
        Question userAnswer = new Question();
        userAnswer.setQuestion_id(questionId);
        userAnswer.setSelected_answer(selectedAnswer);
        
        boolean correct = questionService.checkAnswer(userAnswer);
        
        if (!correct && dataId != null) {
            addToWrongList(dataId, questionId);
        }
        
        return correct ? Result.success("回答正确") : Result.error("回答错误");
    }
    
    private void addToWrongList(Integer dataId, Integer questionId) {
        try {
            List<Integer> mistakeList = new ArrayList<>();
            
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_data WHERE data_id = ?",
                    Integer.class, dataId);
            
            if (count != null && count > 0) {
                Map<String, Object> userData = jdbcTemplate.queryForObject(
                        "SELECT mistake_list_id FROM user_data WHERE data_id = ?",
                        (rs, row) -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("mistake_list_id", readIntArray(rs.getArray("mistake_list_id")));
                            return map;
                        }, dataId);
                
                mistakeList = (List<Integer>) userData.get("mistake_list_id");
                if (mistakeList == null) {
                    mistakeList = new ArrayList<>();
                }
            }
            
            if (!mistakeList.contains(questionId)) {
                mistakeList.add(questionId);
                final List<Integer> finalMistakeList = mistakeList;
                
                if (count != null && count > 0) {
                    jdbcTemplate.update(connection -> {
                        String sql = "UPDATE user_data SET mistake_list_id = ? WHERE data_id = ?";
                        var ps = connection.prepareStatement(sql);
                        ps.setArray(1, connection.createArrayOf("integer", finalMistakeList.toArray(new Integer[0])));
                        ps.setInt(2, dataId);
                        return ps;
                    });
                } else {
                    jdbcTemplate.update(connection -> {
                        String sql = "INSERT INTO user_data (data_id, finished, mistake_numbers, correct_numbers, number_day, collect_numbers, mistake_list_id, correct_list_id, collect_list_id, last_login_date) VALUES (?, 0, 0, 0, 0, 0, ?, ?, ?, NULL)";
                        var ps = connection.prepareStatement(sql);
                        ps.setInt(1, dataId);
                        ps.setArray(2, connection.createArrayOf("integer", finalMistakeList.toArray(new Integer[0])));
                        ps.setArray(3, connection.createArrayOf("integer", new Integer[0]));
                        ps.setArray(4, connection.createArrayOf("integer", new Integer[0]));
                        return ps;
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/wrong")
    public Result<List<Question>> getWrongQuestions(@RequestParam int dataId) {
        return Result.success(questionService.getWrongQuestions(dataId));
    }
    
    @GetMapping("/wrong/ids")
    public Result<List<Integer>> getWrongQuestionIds(@RequestParam int dataId) {
        return Result.success(questionService.getWrongQuestionIds(dataId));
    }
    
    @DeleteMapping("/wrong/delete")
    public Result<String> removeWrongQuestion(@RequestBody Map<String, Object> body) {
        Integer dataId = (Integer) body.get("data_id");
        List<Integer> removeIds = (List<Integer>) body.get("ids");
        
        if (dataId == null) {
            return Result.error("data_id不能为空");
        }
        if (removeIds == null || removeIds.isEmpty()) {
            return Result.error("要删除的ID列表不能为空");
        }
        
        try {
            List<Integer> mistakeList = new ArrayList<>();
            
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_data WHERE data_id = ?",
                    Integer.class, dataId);
            
            if (count != null && count > 0) {
                Map<String, Object> userData = jdbcTemplate.queryForObject(
                        "SELECT mistake_list_id FROM user_data WHERE data_id = ?",
                        (rs, row) -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("mistake_list_id", readIntArray(rs.getArray("mistake_list_id")));
                            return map;
                        }, dataId);
                
                mistakeList = (List<Integer>) userData.get("mistake_list_id");
                if (mistakeList == null) {
                    mistakeList = new ArrayList<>();
                }
            }
            
            final List<Integer> finalMistakeList = mistakeList;
            finalMistakeList.removeAll(removeIds);
            
            jdbcTemplate.update(connection -> {
                String sql = "UPDATE user_data SET mistake_list_id = ? WHERE data_id = ?";
                var ps = connection.prepareStatement(sql);
                ps.setArray(1, connection.createArrayOf("integer", finalMistakeList.toArray(new Integer[0])));
                ps.setInt(2, dataId);
                return ps;
            });
            
            return Result.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("删除失败");
        }
    }

    @PostMapping("/home/collect")
    public Result<String> collectQuestion(@RequestBody Map<String, Object> body) {
        Integer userId = null;
        Integer questionId = null;

        Object userIdObj = body.get("user_id");
        Object idObj = body.get("id");

        if (userIdObj instanceof Integer) {
            userId = (Integer) userIdObj;
        } else if (userIdObj instanceof String) {
            userId = Integer.parseInt((String) userIdObj);
        }

        if (idObj instanceof Integer) {
            questionId = (Integer) idObj;
        } else if (idObj instanceof String) {
            questionId = Integer.parseInt((String) idObj);
        }

        if (userId == null || questionId == null) {
            return Result.error("参数不能为空");
        }

        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            List<Integer> collectList = new ArrayList<>();
            
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_data WHERE data_id = ?",
                    Integer.class, dataId);

            if (count != null && count > 0) {
                Map<String, Object> userData = jdbcTemplate.queryForObject(
                        "SELECT collect_list_id FROM user_data WHERE data_id = ?",
                        (rs, row) -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("collect_list_id", readIntArray(rs.getArray("collect_list_id")));
                            return map;
                        }, dataId);

                collectList = (List<Integer>) userData.get("collect_list_id");
                if (collectList == null) {
                    collectList = new ArrayList<>();
                }
            }

            if (!collectList.contains(questionId)) {
                collectList.add(questionId);
                
                final List<Integer> finalCollectList = collectList;

                if (count != null && count > 0) {
                    jdbcTemplate.update(connection -> {
                        String sql = "UPDATE user_data SET collect_list_id = ?, collect_numbers = ? WHERE data_id = ?";
                        var ps = connection.prepareStatement(sql);
                        ps.setArray(1, connection.createArrayOf("integer", finalCollectList.toArray(new Integer[0])));
                        ps.setInt(2, finalCollectList.size());
                        ps.setInt(3, dataId);
                        return ps;
                    });
                }

                return Result.success("收藏成功");
            } else {
                return Result.success("已收藏");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("操作失败");
        }
    }

    @GetMapping("/home/getCollect")
    public Result<List<Question>> getCollectQuestions(@RequestParam int userId) {
        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            Map<String, Object> userData = jdbcTemplate.queryForObject(
                    "SELECT collect_list_id FROM user_data WHERE data_id = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("collect_list_id", readIntArray(rs.getArray("collect_list_id")));
                        return map;
                    }, dataId);

            List<Integer> collectList = (List<Integer>) userData.get("collect_list_id");
            if (collectList == null || collectList.isEmpty()) {
                return Result.success(new ArrayList<>());
            }

            List<Question> allQuestions = questionService.getAllFromTotal();
            List<Question> collectQuestions = new ArrayList<>();

            for (Integer questionId : collectList) {
                for (Question q : allQuestions) {
                    if (questionId.equals(q.getId())) {
                        collectQuestions.add(q);
                        break;
                    }
                }
            }

            return Result.success(collectQuestions);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取收藏题目失败");
        }
    }

    @DeleteMapping("/home/collectDelete")
    public Result<String> deleteCollect(@RequestBody Map<String, Object> body) {
        Integer userId = getUserIdFromBody(body);
        Integer questionId = getIntegerFromBody(body, "id");

        if (userId == null || questionId == null) {
            return Result.error("参数不能为空");
        }

        try {
            List<Integer> dataIds = jdbcTemplate.queryForList(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataIds == null || dataIds.isEmpty()) {
                return Result.error("用户不存在");
            }
            Integer dataId = dataIds.get(0);

            Map<String, Object> userData = jdbcTemplate.queryForObject(
                    "SELECT collect_list_id FROM user_data WHERE data_id = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("collect_list_id", readIntArray(rs.getArray("collect_list_id")));
                        return map;
                    }, dataId);

            List<Integer> collectList = (List<Integer>) userData.get("collect_list_id");
            if (collectList == null) {
                collectList = new ArrayList<>();
            }

            collectList.remove(Integer.valueOf(questionId));
            final List<Integer> finalCollectList = collectList;

            jdbcTemplate.update(connection -> {
                String sql = "UPDATE user_data SET collect_list_id = ?, collect_numbers = ? WHERE data_id = ?";
                var ps = connection.prepareStatement(sql);
                ps.setArray(1, connection.createArrayOf("integer", finalCollectList.toArray(new Integer[0])));
                ps.setInt(2, finalCollectList.size());
                ps.setInt(3, dataId);
                return ps;
            });

            return Result.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("删除失败");
        }
    }

    @PostMapping("/home/TodayNumber")
    public Result<String> updateTodayNumber(@RequestBody Map<String, Object> body) {
        Integer userId = getUserIdFromBody(body);
        Integer todayNumber = getIntegerFromBody(body, "TodayNumber");

        if (userId == null) {
            return Result.error("用户不存在");
        }

        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            jdbcTemplate.update(
                    "UPDATE \"user_data\" SET \"TodayNumber\" = ? WHERE \"data_id\" = ?",
                    todayNumber != null ? todayNumber : 0, dataId);

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新失败");
        }
    }

    @PostMapping("/home/TodayCorrect")
    public Result<String> updateTodayCorrect(@RequestBody Map<String, Object> body) {
        Integer userId = getUserIdFromBody(body);
        Integer todayCorrect = getIntegerFromBody(body, "TodayCorrect");

        if (userId == null) {
            return Result.error("用户不存在");
        }

        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            jdbcTemplate.update(
                    "UPDATE \"user_data\" SET \"TodayCorrect\" = ? WHERE \"data_id\" = ?",
                    todayCorrect != null ? todayCorrect : 0, dataId);

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新失败");
        }
    }

    @GetMapping("/home/TodayCorrectRate")
    public Result<Integer> getTodayCorrectRate(@RequestParam int userId) {
        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            Map<String, Object> data = jdbcTemplate.queryForObject(
                    "SELECT \"TodayNumber\", \"TodayCorrect\" FROM \"user_data\" WHERE \"data_id\" = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("TodayNumber", rs.getInt("TodayNumber"));
                        map.put("TodayCorrect", rs.getInt("TodayCorrect"));
                        return map;
                    }, dataId);

            int todayNumber = (int) data.get("TodayNumber");
            int todayCorrect = (int) data.get("TodayCorrect");

            int rate = 0;
            if (todayNumber > 0) {
                rate = (int) ((todayCorrect * 100.0) / todayNumber);
            }

            return Result.success(rate);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取失败");
        }
    }

    @PostMapping("/home/WeekData")
    public Result<String> updateWeekData(@RequestBody Map<String, Object> body) {
        Integer userId = getUserIdFromBody(body);

        if (userId == null) {
            return Result.error("用户不存在");
        }

        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            Integer todayNumber = getIntegerFromBody(body, "TodayNumber");
            Integer todayCorrect = getIntegerFromBody(body, "TodayCorrect");

            Integer[] weekData = readIntArrayFromBody(body, "WeekData");
            Integer[] weekCorrect = readIntArrayFromBody(body, "weekCorrect");
            Integer[] weekWrong = readIntArrayFromBody(body, "weekWrong");

            jdbcTemplate.update(connection -> {
                String sql = "UPDATE \"user_data\" SET \"weekData\" = ?, \"weekCorrect\" = ?, \"weekWrong\" = ? WHERE \"data_id\" = ?";
                var ps = connection.prepareStatement(sql);
                ps.setArray(1, connection.createArrayOf("integer", weekData != null ? weekData : new Integer[]{0,0,0,0,0,0,0}));
                ps.setArray(2, connection.createArrayOf("integer", weekCorrect != null ? weekCorrect : new Integer[]{0,0,0,0,0,0,0}));
                ps.setArray(3, connection.createArrayOf("integer", weekWrong != null ? weekWrong : new Integer[]{0,0,0,0,0,0,0}));
                ps.setInt(4, dataId);
                return ps;
            });

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新失败");
        }
    }

    @PostMapping("/home/updataData")
    public Result<String> updataData(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return Result.error("请求体不能为空");
        }

        Integer userId = getUserIdFromBody(body);

        if (userId == null) {
            return Result.error("用户不存在");
        }

        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            Integer todayNumber = getIntegerFromBody(body, "TodayNumber");
            Integer todayCorrect = getIntegerFromBody(body, "TodayCorrect");
            Integer todayCorrectRate = getIntegerFromBody(body, "TodayCorrectRate");

            jdbcTemplate.update(
                    "UPDATE \"user_data\" SET \"TodayNumber\" = ?, \"TodayCorrect\" = ?, \"TodayCorrectRate\" = ? WHERE \"data_id\" = ?",
                    todayNumber != null ? todayNumber : 0,
                    todayCorrect != null ? todayCorrect : 0,
                    todayCorrectRate != null ? todayCorrectRate : 0,
                    dataId);

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新失败");
        }
    }

    @PostMapping("/home/upDataDetailedProblems")
    public Result<String> upDataDetailedProblems(@RequestBody Map<String, Object> body) {
        Integer userId = getIntegerFromBody(body, "user_id");
        Long questionId = getLongFromBody(body, "question_id");
        String typeKey = (String) body.get("type");
        Integer statue = getIntegerFromBody(body, "statue");
        Long time = getLongFromBody(body, "time");

        if (userId == null || questionId == null || typeKey == null) {
            return Result.error("参数不能为空");
        }

        String typeCn = TYPE_MAP.get(typeKey);
        if (typeCn == null) {
            return Result.error("无效的type值: " + typeKey);
        }

        try {
            // 查询是否已存在该用户该类型的该题目记录
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM problems_detail WHERE user_id = ? AND question_id = ? AND type = ?",
                Integer.class, userId, questionId, typeCn);

            if (count != null && count > 0) {
                // 已存在：更新时间戳和做题状态
                jdbcTemplate.update(
                    "UPDATE problems_detail SET time = ?, statue = ? WHERE user_id = ? AND question_id = ? AND type = ?",
                    time != null ? time : 0L, statue != null ? statue : 0, userId, questionId, typeCn);
                return Result.success("更新成功");
            } else {
                // 新题目：插入新记录
                jdbcTemplate.update(
                    "INSERT INTO problems_detail (user_id, type, question_id, statue, time) VALUES (?, ?, ?, ?, ?)",
                    userId, typeCn, questionId, statue != null ? statue : 0, time != null ? time : 0L
                );
                return Result.success("插入成功");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("操作失败");
        }
    }

    /**
     * 每天零点：将前一日数据累积到周数组，然后重置每日数据
     * 周日零点：额外将周数据归零
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyData() {
        System.out.println(">>> [定时任务] 触发时间: " + java.time.LocalDateTime.now());
        try {
            LocalDate today = LocalDate.now();
            DayOfWeek currentDay = today.getDayOfWeek();
            int todayIndex = currentDay.getValue() % 7;
            int prevDayIndex = (todayIndex - 1 + 7) % 7;
            boolean isSunday = currentDay == DayOfWeek.SUNDAY;

            System.out.println(">>> [定时任务] 今天: " + currentDay + "(索引" + todayIndex + "), 前一天索引: " + prevDayIndex + ", 是否周日: " + isSunday);

            List<Map<String, Object>> allData = jdbcTemplate.query(
                "SELECT \"data_id\", \"TodayNumber\", \"TodayCorrect\", \"weekData\", \"weekCorrect\", \"weekWrong\" FROM \"user_data\"",
                (rs, row) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("data_id", rs.getInt("data_id"));
                    map.put("todayNumber", rs.getInt("TodayNumber"));
                    map.put("todayCorrect", rs.getInt("TodayCorrect"));
                    map.put("weekData", readIntArrayToIntArr(rs.getArray("weekData")));
                    map.put("weekCorrect", readIntArrayToIntArr(rs.getArray("weekCorrect")));
                    map.put("weekWrong", readIntArrayToIntArr(rs.getArray("weekWrong")));
                    return map;
                });

            System.out.println(">>> [定时任务] 查询到 " + allData.size() + " 条用户数据");

            for (Map<String, Object> data : allData) {
                int dataId = (int) data.get("data_id");
                final int dId = dataId;

                if (isSunday) {
                    Integer[] zeros = new Integer[]{0, 0, 0, 0, 0, 0, 0};
                    jdbcTemplate.update(connection -> {
                        String sql = "UPDATE \"user_data\" SET \"weekData\" = ?, \"weekCorrect\" = ?, \"weekWrong\" = ?, \"TodayNumber\" = 0, \"TodayCorrect\" = 0, \"TodayCorrectRate\" = 0 WHERE \"data_id\" = ?";
                        var ps = connection.prepareStatement(sql);
                        ps.setArray(1, connection.createArrayOf("integer", zeros));
                        ps.setArray(2, connection.createArrayOf("integer", zeros));
                        ps.setArray(3, connection.createArrayOf("integer", zeros));
                        ps.setInt(4, dId);
                        return ps;
                    });
                } else {
                    int todayNumber = (int) data.get("todayNumber");
                    int todayCorrect = (int) data.get("todayCorrect");
                    Integer[] weekData = (Integer[]) data.get("weekData");
                    Integer[] weekCorrectArr = (Integer[]) data.get("weekCorrect");
                    Integer[] weekWrong = (Integer[]) data.get("weekWrong");

                    if (weekData == null) weekData = new Integer[]{0, 0, 0, 0, 0, 0, 0};
                    if (weekCorrectArr == null) weekCorrectArr = new Integer[]{0, 0, 0, 0, 0, 0, 0};
                    if (weekWrong == null) weekWrong = new Integer[]{0, 0, 0, 0, 0, 0, 0};

                    weekData[prevDayIndex] += todayNumber;
                    weekCorrectArr[prevDayIndex] += todayCorrect;
                    weekWrong[prevDayIndex] += (todayNumber - todayCorrect);

                    System.out.println(">>> [定时任务] data_id=" + dId + " 今日答题: " + todayNumber + " 正确: " + todayCorrect + " 错误: " + (todayNumber - todayCorrect));

                    final Integer[] finalWeekData = weekData;
                    final Integer[] finalWeekCorrect = weekCorrectArr;
                    final Integer[] finalWeekWrong = weekWrong;

                    jdbcTemplate.update(connection -> {
                        String sql = "UPDATE \"user_data\" SET \"weekData\" = ?, \"weekCorrect\" = ?, \"weekWrong\" = ?, \"TodayNumber\" = 0, \"TodayCorrect\" = 0, \"TodayCorrectRate\" = 0 WHERE \"data_id\" = ?";
                        var ps = connection.prepareStatement(sql);
                        ps.setArray(1, connection.createArrayOf("integer", finalWeekData));
                        ps.setArray(2, connection.createArrayOf("integer", finalWeekCorrect));
                        ps.setArray(3, connection.createArrayOf("integer", finalWeekWrong));
                        ps.setInt(4, dId);
                        return ps;
                    });
                }
            }

            if (isSunday) {
                System.out.println(">>> 每周数据已清零，周数据已更新");
            } else {
                System.out.println(">>> 每日数据已零点重置，周数据已更新（" + currentDay + "索引" + prevDayIndex + "）");
            }
        } catch (Exception e) {
            System.err.println(">>> [定时任务] 执行失败!");
            e.printStackTrace();
        }
    }

    /**
     * 记录登录天数：每天首次登录累加，当天重复登录不累加
     */
    @PostMapping("/home/loginDays")
    public Result<Integer> recordLoginDays(@RequestBody Map<String, Object> body) {
        Integer userId = getUserIdFromBody(body);
        if (userId == null) {
            return Result.error("用户不存在");
        }

        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户不存在");
            }

            Map<String, Object> result = jdbcTemplate.queryForObject(
                    "SELECT \"number_day\", \"last_login_date\" FROM \"user_data\" WHERE \"data_id\" = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("number_day", rs.getInt("number_day"));
                        map.put("last_login_date", rs.getDate("last_login_date"));
                        return map;
                    }, dataId);

            int loginDays = (int) result.get("number_day");
            java.sql.Date lastLoginDate = (java.sql.Date) result.get("last_login_date");

            LocalDate today = LocalDate.now();
            boolean isNewDay = (lastLoginDate == null)
                    || !lastLoginDate.toLocalDate().equals(today);

            if (isNewDay) {
                loginDays++;
                jdbcTemplate.update(
                        "UPDATE \"user_data\" SET \"number_day\" = ?, \"last_login_date\" = ? WHERE \"data_id\" = ?",
                        loginDays, java.sql.Date.valueOf(today), dataId);
            }

            return Result.success(loginDays);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("操作失败");
        }
    }

    private Integer getUserIdFromBody(Map<String, Object> body) {
        Object userIdObj = body.get("user_id");
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        } else if (userIdObj instanceof String) {
            try {
                return Integer.parseInt((String) userIdObj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntegerFromBody(Map<String, Object> body, String key) {
        Object obj = body.get(key);
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long getLongFromBody(Map<String, Object> body, String key) {
        Object obj = body.get(key);
        if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        } else if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer[] readIntArrayFromBody(Map<String, Object> body, String key) {
        Object obj = body.get(key);
        if (obj == null) {
            return new Integer[]{0,0,0,0,0,0,0};
        }
        if (obj instanceof Integer[]) {
            return (Integer[]) obj;
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            Integer[] result = new Integer[7];
            for (int i = 0; i < 7 && i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Integer) {
                    result[i] = (Integer) item;
                } else if (item instanceof String) {
                    try {
                        result[i] = Integer.parseInt((String) item);
                    } catch (NumberFormatException e) {
                        result[i] = 0;
                    }
                } else {
                    result[i] = 0;
                }
            }
            for (int i = list.size(); i < 7; i++) {
                result[i] = 0;
            }
            return result;
        }
        return new Integer[]{0,0,0,0,0,0,0};
    }

    @GetMapping("/logical")
    public Result<List<Question>> getLogicalQuestions() {
        return Result.success(questionService.getLogical());
    }

    @GetMapping("/common")
    public Result<List<Question>> getCommonSenseQuestions() {
        return Result.success(questionService.getCommonSense());
    }

    @GetMapping("/speech")
    public Result<List<Question>> getSpeechQuestions() {
        return Result.success(questionService.getSpeech());
    }

    @GetMapping("/quantitative")
    public Result<List<Question>> getQuantitativeQuestions() {
        return Result.success(questionService.getQuantitative());
    }

    @GetMapping("/data")
    public Result<List<Question>> getDataAnalysisQuestions() {
        return Result.success(questionService.getDataAnalysis());
    }

    @GetMapping("/all")
    public Result<Map<String, List<Question>>> getAllModules() {
        List<Question> allQuestions = questionService.getAllFromTotal();
        Map<String, List<Question>> map = new HashMap<>();
        for (Question q : allQuestions) {
            String categoryId = q.getCategory_id();
            map.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(q);
        }
        return Result.success(map);
    }

    @GetMapping("/random")
    public Result<List<Question>> getRandomQuestions(@RequestParam int count) {
        if (count <= 0) {
            return Result.error("数量必须大于0");
        }
        return Result.success(questionService.getRandomQuestions(count));
    }

    // ====================== 登录（修复：接收头像+昵称+自动保存） ======================
    @PostMapping("/login")
    public Result<User> login(@RequestBody User user) {
        String code = user.getCode();
        String avatarUrl = user.getAvatarurl();
        String nickName = user.getNickName();

        if (code == null || code.isEmpty()) {
            return Result.error("请传入code");
        }

        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session"
                    + "?appid=" + wechatAppId
                    + "&secret=" + wechatSecret
                    + "&js_code=" + code
                    + "&grant_type=authorization_code";

            String response = restTemplate.getForObject(url, String.class);
            ObjectMapper mapper = new ObjectMapper();
            WechatSession session = mapper.readValue(response, WechatSession.class);
            String openid = session.getOpenid();

            User loginUser;

            try {
                loginUser = jdbcTemplate.queryForObject(
                        "SELECT * FROM users WHERE wechat_openid = ?",
                        (rs, row) -> {
                            User u = new User();
                            u.setUserId(rs.getInt("user_id"));
                            u.setUsername(rs.getString("username"));
                            u.setAvatarurl(rs.getString("avatar_url"));
                            u.setWechatOpenid(rs.getString("wechat_openid"));
                            u.setDataId(rs.getInt("data_id"));
                            return u;
                        }, openid);

                // 只更新头像，不更新用户名（避免重复）
                jdbcTemplate.update(
                        "UPDATE users SET avatar_url = ? WHERE wechat_openid = ?",
                        avatarUrl, openid);

            } catch (Exception e) {
                jdbcTemplate.update(connection -> {
                        String sql = "INSERT INTO user_data (finished, mistake_numbers, correct_numbers, number_day, collect_numbers, mistake_list_id, correct_list_id, collect_list_id, last_login_date) VALUES (0, 0, 0, 0, 0, ?, ?, ?, NULL)";
                        var ps = connection.prepareStatement(sql);
                        ps.setArray(1, connection.createArrayOf("integer", new Integer[0]));
                        ps.setArray(2, connection.createArrayOf("integer", new Integer[0]));
                        ps.setArray(3, connection.createArrayOf("integer", new Integer[0]));
                        return ps;
                    });
                Integer dataId = jdbcTemplate.queryForObject(
                        "SELECT currval('user_data_data_id_seq')", Integer.class);

                jdbcTemplate.update(
                        "INSERT INTO users (wechat_openid,username,password,avatar_url,phone,data_id) VALUES (?,?,?,?,?,?)",
                        openid, "user_" + openid, "123456", avatarUrl, "", dataId);

                Integer userId = jdbcTemplate.queryForObject(
                        "SELECT currval('users_user_id_seq')", Integer.class);

                loginUser = new User();
                loginUser.setUserId(userId);
                loginUser.setUsername("user_" + openid);
                loginUser.setAvatarurl(avatarUrl);
                loginUser.setDataId(dataId);
            }

            return Result.success(loginUser);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("微信登录失败");
        }
    }

    // ====================== 注册 ======================
    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        String username = user.getUsername();
        String password = user.getPassword();
        String phone = user.getPhone();
        String avatarUrl = user.getAvatarurl();

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return Result.error("用户名和密码不能为空");
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username = ?",
                    Integer.class, username);
            if (count > 0) {
                return Result.error("用户名已存在");
            }
            jdbcTemplate.update(connection -> {
                    String sql = "INSERT INTO user_data (finished, mistake_numbers, correct_numbers, number_day, collect_numbers, mistake_list_id, correct_list_id, collect_list_id, last_login_date) VALUES (0, 0, 0, 0, 0, ?, ?, ?, NULL)";
                    var ps = connection.prepareStatement(sql);
                    ps.setArray(1, connection.createArrayOf("integer", new Integer[0]));
                    ps.setArray(2, connection.createArrayOf("integer", new Integer[0]));
                    ps.setArray(3, connection.createArrayOf("integer", new Integer[0]));
                    return ps;
                });
            Integer dataId = jdbcTemplate.queryForObject("SELECT currval('user_data_data_id_seq')", Integer.class);
            jdbcTemplate.update(
                    "INSERT INTO users (wechat_openid, username, password, avatar_url, phone, data_id) VALUES (?, ?, ?, ?, ?, ?)",
                    "", username, password, avatarUrl, phone, dataId
            );
            return Result.success("注册成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("注册失败：" + e.getMessage());
        }
    }

    // ====================== 用户数据接口 ======================
    @GetMapping("/gainUserData")
    public Result<Map<String, Object>> gainUserData(@RequestParam int dataId) {
        try {
            Map<String, Object> userData = jdbcTemplate.queryForObject(
                    "SELECT \"data_id\", \"finished\", \"mistake_numbers\", \"correct_numbers\", \"number_day\", \"collect_numbers\", " +
                    "\"mistake_list_id\", \"correct_list_id\", \"collect_list_id\", \"TodayNumber\", \"TodayCorrect\", " +
                    "\"TodayCorrectRate\", \"weekData\", \"weekCorrect\", \"weekWrong\", \"last_login_date\" FROM \"user_data\" WHERE \"data_id\" = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("data_id", rs.getInt("data_id"));
                        map.put("finished", rs.getInt("finished"));
                        map.put("mistake_numbers", rs.getInt("mistake_numbers"));
                        map.put("correct_numbers", rs.getInt("correct_numbers"));
                        map.put("number_day", rs.getInt("number_day"));
                        map.put("collect_numbers", rs.getInt("collect_numbers"));
                        map.put("mistake_list_id", readIntArray(rs.getArray("mistake_list_id")));
                        map.put("correct_list_id", readIntArray(rs.getArray("correct_list_id")));
                        map.put("collect_list_id", readIntArray(rs.getArray("collect_list_id")));
                        map.put("TodayNumber", rs.getInt("TodayNumber"));
                        map.put("TodayCorrect", rs.getInt("TodayCorrect"));
                        map.put("TodayCorrectRate", rs.getInt("TodayCorrectRate"));
                        map.put("weekData", readIntArray(rs.getArray("weekData")));
                        map.put("weekCorrect", readIntArray(rs.getArray("weekCorrect")));
                        map.put("weekWrong", readIntArray(rs.getArray("weekWrong")));
                        map.put("last_login_date", rs.getDate("last_login_date"));
                        return map;
                    }, dataId);

            return Result.success(userData);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取用户数据失败");
        }
    }

    @PostMapping("/updateUserData")
    public Result<String> updateUserData(@RequestBody Map<String, Object> body) {
        try {
            Integer dataId = (Integer) body.get("data_id");
            if (dataId == null) {
                return Result.error("data_id不能为空");
            }

            List<String> setClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();

            if (body.containsKey("finished")) {
                setClauses.add("finished = ?");
                params.add(body.get("finished"));
            }
            if (body.containsKey("mistake_numbers")) {
                setClauses.add("mistake_numbers = ?");
                params.add(body.get("mistake_numbers"));
            }
            if (body.containsKey("correct_numbers")) {
                setClauses.add("correct_numbers = ?");
                params.add(body.get("correct_numbers"));
            }
            if (body.containsKey("number_day")) {
                setClauses.add("number_day = ?");
                params.add(body.get("number_day"));
            }
            if (body.containsKey("collect_numbers")) {
                setClauses.add("collect_numbers = ?");
                params.add(body.get("collect_numbers"));
            }
            if (body.containsKey("mistake_list_id")) {
                setClauses.add("mistake_list_id = ?");
                params.add(body.get("mistake_list_id"));
            }
            if (body.containsKey("correct_list_id")) {
                setClauses.add("correct_list_id = ?");
                params.add(body.get("correct_list_id"));
            }
            if (body.containsKey("collect_list_id")) {
                setClauses.add("collect_list_id = ?");
                params.add(body.get("collect_list_id"));
            }

            if (setClauses.isEmpty()) {
                return Result.success("无需更新");
            }

            params.add(dataId);
            String sql = "UPDATE user_data SET " + String.join(", ", setClauses) + " WHERE data_id = ?";

            jdbcTemplate.update(connection -> {
                var ps = connection.prepareStatement(sql);
                int idx = 1;
                for (String clause : setClauses) {
                    Object val = params.get(idx - 1);
                    if (clause.startsWith("mistake_list_id") || clause.startsWith("correct_list_id") || clause.startsWith("collect_list_id")) {
                        Integer[] arr = parseJsonToIntArray((String) val);
                        ps.setArray(idx, connection.createArrayOf("integer", arr));
                    } else {
                        ps.setObject(idx, val);
                    }
                    idx++;
                }
                ps.setInt(idx, dataId);
                return ps;
            });

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新用户数据失败");
        }
    }

    private List<Integer> readIntArray(java.sql.Array sqlArray) {
        if (sqlArray == null) {
            return new ArrayList<>();
        }
        try {
            Integer[] arr = (Integer[]) sqlArray.getArray();
            return arr != null ? new ArrayList<>(Arrays.asList(arr)) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Integer[] readIntArrayToIntArr(java.sql.Array sqlArray) {
        if (sqlArray == null) {
            return new Integer[]{0, 0, 0, 0, 0, 0, 0};
        }
        try {
            Integer[] arr = (Integer[]) sqlArray.getArray();
            return arr != null ? arr : new Integer[]{0, 0, 0, 0, 0, 0, 0};
        } catch (Exception e) {
            return new Integer[]{0, 0, 0, 0, 0, 0, 0};
        }
    }

    private Integer[] parseJsonToIntArray(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new Integer[0];
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (json.trim().startsWith("{")) {
                Map<String, Object> map = mapper.readValue(json, Map.class);
                return map.keySet().stream()
                        .map(Integer::valueOf)
                        .toArray(Integer[]::new);
            }
            return mapper.readValue(json, Integer[].class);
        } catch (Exception e) {
            return new Integer[0];
        }
    }

    // ====================== 微信会话实体 ======================
    public static class WechatSession {
        private String openid;
        private String session_key;
        private String unionid;
        private Integer errcode;
        private String errmsg;

        public String getOpenid() {
            return openid;
        }
        public void setOpenid(String openid) {
            this.openid = openid;
        }
        public String getSession_key() {
            return session_key;
        }
        public void setSession_key(String session_key) {
            this.session_key = session_key;
        }
        public String getUnionid() {
            return unionid;
        }
        public void setUnionid(String unionid) {
            this.unionid = unionid;
        }
        public Integer getErrcode() {
            return errcode;
        }
        public void setErrcode(Integer errcode) {
            this.errcode = errcode;
        }
        public String getErrmsg() {
            return errmsg;
        }
        public void setErrmsg(String errmsg) {
            this.errmsg = errmsg;
        }
    }
}