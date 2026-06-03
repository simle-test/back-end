package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.entity.User;
import org.example.Question;
import org.example.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
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
                        String sql = "INSERT INTO user_data (data_id, mistake_list_id) VALUES (?, ?)";
                        var ps = connection.prepareStatement(sql);
                        ps.setInt(1, dataId);
                        ps.setArray(2, connection.createArrayOf("integer", finalMistakeList.toArray(new Integer[0])));
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
                jdbcTemplate.update(
                        "INSERT INTO user_data (finished,mistake_numbers,correct_numbers,number_day) VALUES (0,0,0,0)");
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
            jdbcTemplate.update(
                    "INSERT INTO user_data (finished, mistake_numbers, correct_numbers, number_day) VALUES (0,0,0,0)"
            );
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
                    "SELECT * FROM user_data WHERE data_id = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("data_id", rs.getInt("data_id"));
                        map.put("finished", rs.getInt("finished"));
                        map.put("mistake_numbers", rs.getInt("mistake_numbers"));
                        map.put("correct_numbers", rs.getInt("correct_numbers"));
                        map.put("number_day", rs.getInt("number_day"));
                        map.put("mistake_list_id", readIntArray(rs.getArray("mistake_list_id")));
                        map.put("correct_lis_id", readIntArray(rs.getArray("correct_lis_id")));
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
            if (body.containsKey("mistake_list_id")) {
                setClauses.add("mistake_list_id = ?");
                params.add(body.get("mistake_list_id"));
            }
            if (body.containsKey("correct_lis_id")) {
                setClauses.add("correct_lis_id = ?");
                params.add(body.get("correct_lis_id"));
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
                    if (clause.startsWith("mistake_list_id") || clause.startsWith("correct_lis_id")) {
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