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
    public Result<String> submitAnswer(@RequestBody Question userAnswer) {
        boolean correct = questionService.checkAnswer(userAnswer);
        return correct ? Result.success("回答正确") : Result.error("回答错误");
    }

    @GetMapping("/wrong")
    public Result<List<Question>> getWrongQuestions() {
        return Result.success(questionService.getWrongQuestions());
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
                // 查询用户
                loginUser = jdbcTemplate.queryForObject(
                        "SELECT * FROM users WHERE wechat_openid = ?",
                        (rs, row) -> {
                            User u = new User();
                            u.setUserId(rs.getInt("user_id"));
                            u.setUsername(rs.getString("username"));
                            u.setAvatarurl(rs.getString("avatar_url"));
                            u.setWechatOpenid(rs.getString("wechat_openid"));
                            return u;
                        }, openid);

                // 只更新头像，不更新用户名（避免重复）
                jdbcTemplate.update(
                        "UPDATE users SET avatar_url = ? WHERE wechat_openid = ?",
                        avatarUrl, openid);

            } catch (Exception e) {
                // 注册新用户 —— 用户名使用 openid 保证唯一
                jdbcTemplate.update(
                        "INSERT INTO user_data (finished,mistake_numbers,correct_numbers,number_day) VALUES (0,0,0,0)");
                Integer dataId = jdbcTemplate.queryForObject(
                        "SELECT currval('user_data_data_id_seq')", Integer.class);

                // ✅ 修复：用户名不再重复
                jdbcTemplate.update(
                        "INSERT INTO users (wechat_openid,username,password,avatar_url,phone,data_id) VALUES (?,?,?,?,?,?)",
                        openid, "user_" + openid, "123456", avatarUrl, "", dataId);

                loginUser = new User();
                loginUser.setUsername("user_" + openid);
                loginUser.setAvatarurl(avatarUrl);
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
    public Result<Map<String, Object>> gainUserData(@RequestParam int userId) {
        try {
            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户数据不存在");
            }

            Map<String, Object> userData = jdbcTemplate.queryForObject(
                    "SELECT * FROM user_data WHERE data_id = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("dataId", rs.getInt("data_id"));
                        map.put("finished", rs.getInt("finished"));
                        map.put("mistakeNumbers", rs.getInt("mistake_numbers"));
                        map.put("correctNumbers", rs.getInt("correct_numbers"));
                        map.put("numberDay", rs.getInt("number_day"));
                        map.put("mistakeListId", parseJsonToIntList(rs.getString("mistake_list_id")));
                        map.put("correctLisId", parseJsonToIntList(rs.getString("correct_lis_id")));
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
            Integer userId = (Integer) body.get("userId");
            if (userId == null) {
                return Result.error("userId不能为空");
            }

            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT data_id FROM users WHERE user_id = ?",
                    Integer.class, userId);

            if (dataId == null) {
                return Result.error("用户数据不存在");
            }

            List<String> setClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();

            if (body.containsKey("finished")) {
                setClauses.add("finished = ?");
                params.add(body.get("finished"));
            }
            if (body.containsKey("mistakeNumbers")) {
                setClauses.add("mistake_numbers = ?");
                params.add(body.get("mistakeNumbers"));
            }
            if (body.containsKey("correctNumbers")) {
                setClauses.add("correct_numbers = ?");
                params.add(body.get("correctNumbers"));
            }
            if (body.containsKey("numberDay")) {
                setClauses.add("number_day = ?");
                params.add(body.get("numberDay"));
            }
            if (body.containsKey("mistakeListId")) {
                setClauses.add("mistake_list_id = ?::json");
                params.add(new ObjectMapper().writeValueAsString(body.get("mistakeListId")));
            }
            if (body.containsKey("correctLisId")) {
                setClauses.add("correct_lis_id = ?::json");
                params.add(new ObjectMapper().writeValueAsString(body.get("correctLisId")));
            }

            if (setClauses.isEmpty()) {
                return Result.success("无需更新");
            }

            params.add(dataId);
            String sql = "UPDATE user_data SET " + String.join(", ", setClauses) + " WHERE data_id = ?";
            jdbcTemplate.update(sql, params.toArray());

            return Result.success("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新用户数据失败");
        }
    }

    private List<Integer> parseJsonToIntList(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return Arrays.asList(mapper.readValue(json, Integer[].class));
        } catch (Exception e) {
            return new ArrayList<>();
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