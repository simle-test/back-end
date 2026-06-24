package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.LinkedHashMap;
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
    private org.example.mapper.QuestionMapper questionMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${wechat.appid}")
    private String wechatAppId;

    @Value("${wechat.secret}")
    private String wechatSecret;

    // type 转换表：前端英文 key -> 数据库中文
    private static final Map<String, String> TYPE_MAP = new HashMap<>();
    static {
        // 完整 key
        TYPE_MAP.put("language_understanding", "言语理解");
        TYPE_MAP.put("quantity_relation", "数量关系");
        TYPE_MAP.put("logical_judgment", "判断推理");
        TYPE_MAP.put("material_analysis", "资料分析");
        TYPE_MAP.put("common_sense_judgment", "常识判断");
        TYPE_MAP.put("political_theory", "政治理论");
        // 短 key（前端 currentType 直接使用）
        TYPE_MAP.put("speech", "言语理解");
        TYPE_MAP.put("quantitative", "数量关系");
        TYPE_MAP.put("logical", "判断推理");
        TYPE_MAP.put("data", "资料分析");
        TYPE_MAP.put("common", "常识判断");
        TYPE_MAP.put("political", "政治理论");
    }

    // 反向转换表：数据库中文 -> 前端英文 key
    private static final Map<String, String> REVERSE_TYPE_MAP = new HashMap<>();
    static {
        REVERSE_TYPE_MAP.put("言语理解", "language_understanding");
        REVERSE_TYPE_MAP.put("数量关系", "quantity_relation");
        REVERSE_TYPE_MAP.put("判断推理", "logical_judgment");
        REVERSE_TYPE_MAP.put("资料分析", "material_analysis");
        REVERSE_TYPE_MAP.put("常识判断", "common_sense_judgment");
        REVERSE_TYPE_MAP.put("政治理论", "political_theory");
    }

    // ====================== SM-2 间隔重复算法常量 ======================
    /** 初始难易度因子（SuperMemo 默认 2.5） */
    private static final double INITIAL_EF = 2.5;
    /** 最低难易度因子 */
    private static final double MIN_EF = 1.3;
    /** 达到此连续答对次数即标记"已掌握"，从错题表移除 */
    private static final int MASTERY_THRESHOLD = 3;

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
        Integer userId = getIntegerFromBody(body, "user_id");

        Question userAnswer = new Question();
        userAnswer.setQuestion_id(questionId);
        userAnswer.setSelected_answer(selectedAnswer);

        boolean correct = questionService.checkAnswer(userAnswer);

        if (dataId != null) {
            updateDailyStats(dataId, correct);
        }

        if (dataId != null) {
            // SM-2 间隔重复算法
            if (correct) {
                processCorrectAnswer(dataId, questionId);
            } else {
                processWrongAnswer(dataId, questionId);
            }
        }

        return correct ? Result.success("回答正确") : Result.error("回答错误");
    }

    // ====================== SM-2 间隔重复算法核心方法 ======================

    /**
     * 加载指定题目的 SRS 状态
     */
    private Map<String, Object> loadSrsState(Integer dataId, Integer questionId) {
        try {
            String json = jdbcTemplate.queryForObject(
                "SELECT question_status->?::text FROM user_data WHERE data_id = ?",
                String.class, questionId.toString(), dataId);
            if (json == null || json.isEmpty()) return null;
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 保存指定题目的 SRS 状态
     */
    private void saveSrsState(Integer dataId, Integer questionId, Map<String, Object> state) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(state);
            jdbcTemplate.update(
                "UPDATE user_data SET question_status = jsonb_set(COALESCE(question_status, '{}'), ?::text[], ?::jsonb, true) WHERE data_id = ?",
                new String[]{questionId.toString()}, json, dataId);
            // 同步更新 question_status 列中的掌握/未掌握标记（由调用方负责）
        } catch (Exception e) {
            System.err.println("[SRS] 保存状态失败 dataId=" + dataId + " qid=" + questionId + ": " + e.getMessage());
        }
    }

    /**
     * 答对：SM-2 算法 — 到期复习才累加 n，提前复习只记录不变 n
     */
    private void processCorrectAnswer(Integer dataId, Integer questionId) {
        Map<String, Object> srs = loadSrsState(dataId, questionId);
        LocalDate today = LocalDate.now();

        double ef = INITIAL_EF;
        int n = 0;
        int interval = 0;
        LocalDate lastNextReview = null;

        if (srs != null) {
            Object efObj = srs.get("ef");
            Object nObj = srs.get("n");
            Object ivObj = srs.get("interval");
            Object nrObj = srs.get("nextReview");
            ef = efObj != null ? ((Number) efObj).doubleValue() : INITIAL_EF;
            n = nObj != null ? ((Number) nObj).intValue() : 0;
            interval = ivObj != null ? ((Number) ivObj).intValue() : 0;
            if (nrObj != null) {
                try { lastNextReview = LocalDate.parse(nrObj.toString()); } catch (Exception e) { /* ignore */ }
            }
        }

        // 只有复习到期（或首次）才累加 n；提前答对只记录，不推进
        boolean isReview = (lastNextReview == null) || !today.isBefore(lastNextReview);

        if (isReview) {
            n = n + 1;
            if (n == 1) {
                interval = 1;
            } else if (n == 2) {
                interval = 3;
            } else {
                interval = (int) Math.round(interval * ef);
            }
            ef = Math.max(MIN_EF, ef + 0.1);
        }
        // 提前答对：n / ef / interval 都不变，只更新 nextReview 为今天（避免今天反复刷）

        Map<String, Object> newState = new LinkedHashMap<>();
        newState.put("ef", ef);
        newState.put("n", n);
        newState.put("interval", interval);
        newState.put("nextReview", today.plusDays(isReview ? interval : 1).toString());
        saveSrsState(dataId, questionId, newState);

        if (n >= MASTERY_THRESHOLD) {
            markAsMastered(dataId, questionId);
        }

        System.out.println("[SRS] 答对 qid=" + questionId + " n=" + n + " isReview=" + isReview + " interval=" + interval);
    }

    /**
     * 答错：重置 n，降低 EF，重新加入错题表
     */
    private void processWrongAnswer(Integer dataId, Integer questionId) {
        Map<String, Object> srs = loadSrsState(dataId, questionId);
        double ef = INITIAL_EF;
        int oldN = 0;

        if (srs != null) {
            Object efObj = srs.get("ef");
            Object nObj = srs.get("n");
            ef = efObj != null ? ((Number) efObj).doubleValue() : INITIAL_EF;
            oldN = nObj != null ? ((Number) nObj).intValue() : 0;
        }

        ef = Math.max(MIN_EF, ef - 0.2);

        Map<String, Object> newState = new LinkedHashMap<>();
        newState.put("ef", ef);
        newState.put("n", 0);
        newState.put("interval", 0);
        newState.put("nextReview", LocalDate.now().toString());
        saveSrsState(dataId, questionId, newState);

        addToWrongList(dataId, questionId);

        System.out.println("[SRS] 答错 qid=" + questionId + " n=" + oldN + "→0 ef=" + ef);
    }

    /**
     * 标记题目为已掌握：从错题表移除，加入已掌握列表
     */
    private void markAsMastered(Integer dataId, Integer questionId) {
        try {
            // 1. 从错题表移除
            List<Integer> mistakeList = loadMistakeList(dataId);
            mistakeList.remove(questionId);
            updateMistakeList(dataId, mistakeList);

            // 2. 加入 correct_list_id（已掌握列表）
            List<Integer> correctList = loadIntArrayColumn(dataId, "correct_list_id");
            if (!correctList.contains(questionId)) {
                correctList.add(questionId);
                updateIntArrayColumn(dataId, "correct_list_id", correctList);
            }

            System.out.println("[SRS] 已掌握 qid=" + questionId + " dataId=" + dataId);
        } catch (Exception e) {
            System.err.println("[SRS] 标记已掌握失败: " + e.getMessage());
        }
    }

    // ====================== 辅助 SQL 方法 ======================

    private List<Integer> loadMistakeList(Integer dataId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForObject(
                "SELECT mistake_list_id FROM user_data WHERE data_id = ?",
                (rs, rn) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("mistake_list_id", readIntArray(rs.getArray("mistake_list_id")));
                    return map;
                }, dataId);
            List<Integer> list = (List<Integer>) row.get("mistake_list_id");
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void updateMistakeList(Integer dataId, List<Integer> list) {
        final List<Integer> finalList = list;
        jdbcTemplate.update(connection -> {
            String sql = "UPDATE user_data SET mistake_list_id = ? WHERE data_id = ?";
            var ps = connection.prepareStatement(sql);
            ps.setArray(1, connection.createArrayOf("integer", finalList.toArray(new Integer[0])));
            ps.setInt(2, dataId);
            return ps;
        });
    }

    private List<Integer> loadIntArrayColumn(Integer dataId, String column) {
        try {
            String sql = String.format("SELECT %s FROM user_data WHERE data_id = ?", column);
            Map<String, Object> row = jdbcTemplate.queryForObject(sql,
                (rs, rn) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put(column, readIntArray(rs.getArray(column)));
                    return map;
                }, dataId);
            List<Integer> list = (List<Integer>) row.get(column);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void updateIntArrayColumn(Integer dataId, String column, List<Integer> list) {
        final List<Integer> finalList = list;
        jdbcTemplate.update(connection -> {
            String sql = String.format("UPDATE user_data SET %s = ? WHERE data_id = ?", column);
            var ps = connection.prepareStatement(sql);
            ps.setArray(1, connection.createArrayOf("integer", finalList.toArray(new Integer[0])));
            ps.setInt(2, dataId);
            return ps;
        });
    }

    private void updateDailyStats(Integer dataId, boolean correct) {
        try {
            // 直接用 SQL 计算并更新，COALESCE 处理 NULL
            int updated = jdbcTemplate.update(
                    "UPDATE user_data SET " +
                    "\"TodayNumber\" = COALESCE(\"TodayNumber\", 0) + 1, " +
                    "\"TodayCorrect\" = COALESCE(\"TodayCorrect\", 0) + ?, " +
                    "\"TodayCorrectRate\" = CAST((COALESCE(\"TodayCorrect\", 0) + ?) * 100.0 / (COALESCE(\"TodayNumber\", 0) + 1) AS INTEGER) " +
                    "WHERE data_id = ?",
                    correct ? 1 : 0, correct ? 1 : 0, dataId);

            if (updated == 0) {
                // 行不存在，新建
                int todayCorrect = correct ? 1 : 0;
                int rate = correct ? 100 : 0;
                jdbcTemplate.update(connection -> {
                    String sql = "INSERT INTO user_data (data_id, finished, mistake_numbers, correct_numbers, number_day, collect_numbers, mistake_list_id, correct_list_id, collect_list_id, question_status, \"TodayNumber\", \"TodayCorrect\", \"TodayCorrectRate\", last_login_date) VALUES (?, 0, 0, 0, 0, 0, ?, ?, ?, '{}', 1, ?, ?, NULL)";
                    var ps = connection.prepareStatement(sql);
                    ps.setInt(1, dataId);
                    ps.setArray(2, connection.createArrayOf("integer", new Integer[0]));
                    ps.setArray(3, connection.createArrayOf("integer", new Integer[0]));
                    ps.setArray(4, connection.createArrayOf("integer", new Integer[0]));
                    ps.setInt(5, todayCorrect);
                    ps.setInt(6, rate);
                    return ps;
                });
            }

            System.out.println("[正确率更新] dataId=" + dataId + " 答" + (correct ? "对" : "错") + " 影响行数=" + (updated > 0 ? updated : "新建"));
        } catch (Exception e) {
            System.err.println("[正确率更新失败] dataId=" + dataId + " error=" + e.getMessage());
            e.printStackTrace();
        }
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
                        String sql = "INSERT INTO user_data (data_id, finished, mistake_numbers, correct_numbers, number_day, collect_numbers, mistake_list_id, correct_list_id, collect_list_id, question_status, last_login_date) VALUES (?, 0, 0, 0, 0, 0, ?, ?, ?, '{}', NULL)";
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

            recalcCorrectRate(dataId);

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

            recalcCorrectRate(dataId);

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新失败");
        }
    }

    @GetMapping("/problem/gainProblemDetail")
    public Result<List<Question>> gainProblemDetail(@RequestParam int user_id) {
        try {
            // 1. 从 problems_detail 查询该用户最近3道题（按最新时间戳排序）
            List<Long> ids = jdbcTemplate.query(
                "SELECT question_id, MAX(time) AS t FROM problems_detail WHERE user_id = ? GROUP BY question_id ORDER BY t DESC LIMIT 3",
                (rs, row) -> rs.getLong("question_id"),
                user_id);

            if (ids.isEmpty()) {
                return Result.success(new ArrayList<>());
            }

            // 2. 转为 Integer 列表给 MyBatis 查询
            List<Integer> intIds = new ArrayList<>();
            for (Long id : ids) {
                intIds.add(id.intValue());
            }

            // 3. 从 questions_total 批量查询题目详情
            List<Question> questions = questionMapper.findByTotalIds(intIds);

            return Result.success(questions);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("查询失败");
        }
    }

    /** 根据当前 TodayNumber 和 TodayCorrect 重新计算并更新 TodayCorrectRate */
    private void recalcCorrectRate(Integer dataId) {
        jdbcTemplate.update(
                "UPDATE \"user_data\" SET \"TodayCorrectRate\" = " +
                "CASE WHEN COALESCE(\"TodayNumber\", 0) > 0 " +
                "THEN CAST(COALESCE(\"TodayCorrect\", 0) * 100.0 / \"TodayNumber\" AS INTEGER) " +
                "ELSE 0 END " +
                "WHERE \"data_id\" = ?",
                dataId);
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

            int tn = todayNumber != null ? todayNumber : 0;
            int tc = todayCorrect != null ? todayCorrect : 0;
            int rate = tn > 0 ? (int) (tc * 100.0 / tn) : 0;

            jdbcTemplate.update(
                    "UPDATE \"user_data\" SET \"TodayNumber\" = ?, \"TodayCorrect\" = ?, \"TodayCorrectRate\" = ? WHERE \"data_id\" = ?",
                    tn, tc, rate, dataId);

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
        String selectedOption = (String) body.get("selected_option");

        if (userId == null || questionId == null || typeKey == null) {
            return Result.error("参数不能为空");
        }

        String typeCn = TYPE_MAP.get(typeKey);
        if (typeCn == null) {
            return Result.error("无效的type值: " + typeKey);
        }

        try {
            // 每次答题都插入新记录，不做去重，以便统计错误次数
            jdbcTemplate.update(
                "INSERT INTO problems_detail (user_id, type, question_id, statue, time, selected_option) VALUES (?, ?, ?, ?, ?, ?)",
                userId, typeCn, questionId, statue != null ? statue : 0, time != null ? time : 0L, selectedOption
            );

            // SRS 间隔重复处理：根据 statue 判断对错
            Integer dataId = jdbcTemplate.queryForObject(
                "SELECT data_id FROM users WHERE user_id = ?", Integer.class, userId);
            if (dataId != null) {
                boolean isCorrect = statue != null && statue == 1;
                if (isCorrect) {
                    processCorrectAnswer(dataId, questionId.intValue());
                } else {
                    processWrongAnswer(dataId, questionId.intValue());
                }
            }

            return Result.success("操作成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("操作失败");
        }
    }

    @GetMapping("/home/problems_detail")
    public Result<List<Map<String, Object>>> getProblemsDetail(@RequestParam int user_id) {
        try {
            List<Map<String, Object>> list = jdbcTemplate.query(
                "SELECT user_id, type, question_id, statue, time, selected_option FROM problems_detail WHERE user_id = ?",
                (rs, row) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("user_id", rs.getInt("user_id"));
                    String typeCn = rs.getString("type");
                    map.put("type", REVERSE_TYPE_MAP.getOrDefault(typeCn, typeCn));
                    map.put("question_id", rs.getLong("question_id"));
                    map.put("statue", rs.getInt("statue"));
                    map.put("time", rs.getLong("time"));
                    map.put("selected_option", rs.getString("selected_option"));
                    return map;
                }, user_id);
            return Result.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("查询失败");
        }
    }

    /**
     * 模块正确率分析：按题型聚合做题记录，返回每种题型的 total / correct
     */
    @GetMapping("/analysis/moduleAccuracy")
    public Result<List<Map<String, Object>>> getModuleAccuracy(@RequestParam int dataId) {
        try {
            // users 表才有 user_id，user_data 只有 data_id
            Integer userId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM users WHERE data_id = ?",
                    Integer.class, dataId);
            if (userId == null) {
                return Result.error("用户不存在");
            }

            List<Map<String, Object>> list = jdbcTemplate.query(
                    "SELECT type, COUNT(*) AS total, " +
                    "SUM(CASE WHEN statue = 1 THEN 1 ELSE 0 END) AS correct " +
                    "FROM problems_detail WHERE user_id = ? " +
                    "GROUP BY type ORDER BY type",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        String typeCn = rs.getString("type");
                        map.put("type", typeCn);
                        map.put("total", rs.getInt("total"));
                        map.put("correct", rs.getInt("correct"));
                        return map;
                    }, userId);
            return Result.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("查询失败");
        }
    }

    /**
     * 模块完成进度：该题型用户已做过的题目数 / 该题型总题目数
     */
    @GetMapping("/analysis/moduleProgress")
    public Result<List<Map<String, Object>>> getModuleProgress(@RequestParam int dataId) {
        try {
            Integer userId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM users WHERE data_id = ?",
                    Integer.class, dataId);
            if (userId == null) {
                return Result.error("用户不存在");
            }

            Map<String, Integer> doneMap = new HashMap<>();
            jdbcTemplate.query(
                    "SELECT type, COUNT(DISTINCT question_id) AS done " +
                    "FROM problems_detail WHERE user_id = ? GROUP BY type",
                    (rs) -> { doneMap.put(rs.getString("type"), rs.getInt("done")); },
                    userId);

            Map<String, Integer> totalMap = new HashMap<>();
            jdbcTemplate.query(
                    "SELECT category_id, COUNT(*) AS total FROM questions_total GROUP BY category_id",
                    (rs) -> {
                        String catId = rs.getString("category_id");
                        totalMap.put(TYPE_MAP.getOrDefault(catId, catId), rs.getInt("total"));
                    });

            String[] order = {"言语理解", "数量关系", "判断推理", "资料分析", "常识判断", "政治理论"};
            List<Map<String, Object>> result = new ArrayList<>();
            for (String name : order) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", name);
                map.put("total", totalMap.getOrDefault(name, 0));
                map.put("done", doneMap.getOrDefault(name, 0));
                result.add(map);
            }
            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("查询失败");
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

    @GetMapping("/select")
    public Result<List<Question>> searchQuestions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") int limit) {
        return Result.success(questionService.searchQuestions(keyword, limit));
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

    // ====================== 考试试卷接口 ======================

    @GetMapping("/exam/papers")
    public Result<List<Map<String, Object>>> getExamPapers() {
        try {
            List<Map<String, Object>> papers = jdbcTemplate.query(
                "SELECT source, COUNT(*) AS count FROM questions_total WHERE source IS NOT NULL AND source != '' GROUP BY source ORDER BY count DESC",
                (rs, row) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("source", rs.getString("source"));
                    map.put("count", rs.getInt("count"));
                    return map;
                });
            return Result.success(papers);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取试卷列表失败");
        }
    }

    @GetMapping("/exam/problems")
    public Result<List<Question>> getExamProblems(@RequestParam String source) {
        try {
            List<Question> all = questionService.getAllFromTotal();
            List<Question> filtered = new ArrayList<>();
            for (Question q : all) {
                if (source.equals(q.getSource())) {
                    filtered.add(q);
                }
            }
            return Result.success(filtered);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取试卷题目失败");
        }
    }

    // ====================== 用户数据接口 ======================
    @GetMapping("/gainUserData")
    public Result<Map<String, Object>> gainUserData(@RequestParam int dataId) {
        try {
            Map<String, Object> userData = jdbcTemplate.queryForObject(
                    "SELECT \"data_id\", \"finished\", \"mistake_numbers\", \"correct_numbers\", \"number_day\", \"collect_numbers\", " +
                    "\"mistake_list_id\", \"correct_list_id\", \"collect_list_id\", \"TodayNumber\", \"TodayCorrect\", " +
                    "\"TodayCorrectRate\", \"weekData\", \"weekCorrect\", \"weekWrong\", \"last_login_date\", \"question_status\" FROM \"user_data\" WHERE \"data_id\" = ?",
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
                        map.put("question_status", rs.getString("question_status"));
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
                        // 前端可能传 JSON 字符串，也可能传 ArrayList
                        Integer[] arr = objectToIntArray(val);
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

    /** 清除用户所有做题相关数据 */
    @PostMapping("/delUserData")
    public Result<String> delUserData(@RequestParam("data_id") Integer dataId) {
        if (dataId == null) {
            return Result.error("data_id不能为空");
        }

        try {
            // 1. 清除 problems_detail 表中该用户的所有记录
            List<Integer> userIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM users WHERE data_id = ?", Integer.class, dataId);
            if (!userIds.isEmpty()) {
                Integer userId = userIds.get(0);
                jdbcTemplate.update("DELETE FROM problems_detail WHERE user_id = ?", userId);
            }

            // 2. 重置 user_data 行
            jdbcTemplate.update(connection -> {
                String sql = "UPDATE user_data SET " +
                    "finished = 0, " +
                    "mistake_numbers = 0, " +
                    "correct_numbers = 0, " +
                    "number_day = 0, " +
                    "collect_numbers = 0, " +
                    "mistake_list_id = ?, " +
                    "correct_list_id = ?, " +
                    "collect_list_id = ?, " +
                    "question_status = '{}', " +
                    "\"TodayNumber\" = 0, " +
                    "\"TodayCorrect\" = 0, " +
                    "\"TodayCorrectRate\" = 0, " +
                    "\"weekData\" = ?, " +
                    "\"weekCorrect\" = ?, " +
                    "\"weekWrong\" = ? " +
                    "WHERE data_id = ?";
                var ps = connection.prepareStatement(sql);
                ps.setArray(1, connection.createArrayOf("integer", new Integer[0]));
                ps.setArray(2, connection.createArrayOf("integer", new Integer[0]));
                ps.setArray(3, connection.createArrayOf("integer", new Integer[0]));
                ps.setArray(4, connection.createArrayOf("integer", new Integer[]{0,0,0,0,0,0,0}));
                ps.setArray(5, connection.createArrayOf("integer", new Integer[]{0,0,0,0,0,0,0}));
                ps.setArray(6, connection.createArrayOf("integer", new Integer[]{0,0,0,0,0,0,0}));
                ps.setInt(7, dataId);
                return ps;
            });

            return Result.success("用户数据已重置");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("重置失败");
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

    /**
     * 将 Object 转为 Integer[]，兼容 String（JSON）、ArrayList、Integer[]
     */
    private Integer[] objectToIntArray(Object val) {
        if (val == null) return new Integer[0];
        if (val instanceof String) {
            return parseJsonToIntArray((String) val);
        }
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            return list.stream()
                    .map(item -> item instanceof Number ? ((Number) item).intValue() : Integer.valueOf(String.valueOf(item)))
                    .toArray(Integer[]::new);
        }
        if (val instanceof Integer[]) {
            return (Integer[]) val;
        }
        if (val instanceof Object[]) {
            Object[] arr = (Object[]) val;
            Integer[] result = new Integer[arr.length];
            for (int i = 0; i < arr.length; i++) {
                result[i] = arr[i] instanceof Number ? ((Number) arr[i]).intValue() : Integer.valueOf(String.valueOf(arr[i]));
            }
            return result;
        }
        return new Integer[0];
    }

    // ====================== 更新用户基本信息接口 ======================

    /**
     * 更新 users 表中的 username 和 phone
     */
    @PostMapping("/upUserData")
    public Result<String> upUserData(@RequestBody Map<String, Object> body) {
        Integer userId = getUserIdFromBody(body);
        if (userId == null) {
            return Result.error("user_id不能为空");
        }

        try {
            // 检查用户是否存在
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE user_id = ?", Integer.class, userId);
            if (count == null || count == 0) {
                return Result.error("用户不存在");
            }

            StringBuilder sql = new StringBuilder("UPDATE users SET ");
            List<Object> params = new ArrayList<>();
            List<String> setClauses = new ArrayList<>();

            if (body.containsKey("username")) {
                String username = (String) body.get("username");
                if (username != null && !username.trim().isEmpty()) {
                    setClauses.add("username = ?");
                    params.add(username.trim());
                }
            }
            if (body.containsKey("phone")) {
                String phone = (String) body.get("phone");
                if (phone != null && !phone.trim().isEmpty()) {
                    setClauses.add("phone = ?");
                    params.add(phone.trim());
                }
            }

            if (setClauses.isEmpty()) {
                return Result.success("无需更新");
            }

            sql.append(String.join(", ", setClauses));
            sql.append(" WHERE user_id = ?");
            params.add(userId);

            jdbcTemplate.update(sql.toString(), params.toArray());

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新用户信息失败");
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