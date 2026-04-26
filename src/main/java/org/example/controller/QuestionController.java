package org.example.controller;

import org.example.common.Result;
import org.example.entity.User;
import org.example.Question;
import org.example.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate; // 引入 RestTemplate

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 用于调用微信接口
    private final RestTemplate restTemplate = new RestTemplate();

    // ====================== 题目接口（保持原样） ======================
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

    @GetMapping("/list")
    public Result<List<Question>> listQuestions() {
        return Result.success(questionService.getAllQuestions());
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
        Map<String, List<Question>> map = new HashMap<>();
        map.put("logical", questionService.getLogical());
        map.put("common", questionService.getCommonSense());
        map.put("speech", questionService.getSpeech());
        map.put("quantitative", questionService.getQuantitative());
        map.put("data", questionService.getDataAnalysis());
        return Result.success(map);
    }

    // ====================== 登录接口（已修改：支持微信自动登录/注册） ======================
    @PostMapping("/login")
    public Result<User> login(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String code // 前端传来的 code
    ) {
        User user = null;
        String wechatOpenid = null;

        // ====== 1. 微信登录逻辑 ======
        if (code != null && !code.trim().isEmpty()) {
            // 你的微信小程序 AppID 和 AppSecret (请替换成你自己的)
            String appId = "YOUR_APPID_HERE";
            String secret = "YOUR_APPSECRET_HERE";
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId +
                    "&secret=" + secret + "&js_code=" + code + "&grant_type=authorization_code";

            try {
                // 调用微信接口获取 OpenID
                WechatSession session = restTemplate.getForObject(url, WechatSession.class);

                if (session != null && session.getOpenid() != null) {
                    wechatOpenid = session.getOpenid();

                    // 1. 尝试查询用户
                    try {
                        user = jdbcTemplate.queryForObject(
                                "SELECT * FROM users WHERE wechat_openid = ?",
                                (rs, row) -> {
                                    User u = new User();
                                    u.setUserId(rs.getInt("user_id"));
                                    u.setUsername(rs.getString("username"));
                                    u.setPassword(rs.getString("password"));
                                    u.setPhone(rs.getString("phone"));
                                    u.setAvatarUrl(rs.getString("avatar_url"));
                                    u.setWechatOpenid(rs.getString("wechat_openid"));
                                    u.setDataId(rs.getInt("data_id"));
                                    return u;
                                }, wechatOpenid);
                    } catch (Exception e) {
                        // 用户不存在，执行自动注册
                        // 1.1 先插入 user_data
                        jdbcTemplate.update(
                                "INSERT INTO user_data (finished, mistake_numbers, correct_numbers, number_day) VALUES (0,0,0,0)"
                        );
                        Integer dataId = jdbcTemplate.queryForObject("SELECT currval('user_data_data_id_seq')", Integer.class);

                        // 1.2 插入 users 表
                        String defaultUsername = "wx_user_" + (int)(Math.random() * 10000);
                        jdbcTemplate.update(
                                "INSERT INTO users (username, password, phone, avatar_url, wechat_openid, data_id) VALUES (?, ?, ?, ?, ?, ?)",
                                defaultUsername, "123456", null, null, wechatOpenid, dataId
                        );

                        // 1.3 查询刚注册的用户返回
                        user = jdbcTemplate.queryForObject(
                                "SELECT * FROM users WHERE wechat_openid = ?",
                                (rs, row) -> {
                                    User u = new User();
                                    u.setUserId(rs.getInt("user_id"));
                                    u.setUsername(rs.getString("username"));
                                    u.setPassword(rs.getString("password"));
                                    u.setPhone(rs.getString("phone"));
                                    u.setAvatarUrl(rs.getString("avatar_url"));
                                    u.setWechatOpenid(rs.getString("wechat_openid"));
                                    u.setDataId(rs.getInt("data_id"));
                                    return u;
                                }, wechatOpenid);
                    }
                } else {
                    return Result.error("微信登录失败: " + (session != null ? session.getErrmsg() : "未知错误"));
                }
            } catch (Exception e) {
                return Result.error("请求微信服务器失败");
            }
        }
        // ====== 2. 账号密码登录（保持原样） ======
        else {
            if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
                return Result.error("请输入账号密码");
            }
            try {
                user = jdbcTemplate.queryForObject(
                        "SELECT * FROM users WHERE username = ? AND password = ?",
                        (rs, row) -> {
                            User u = new User();
                            u.setUserId(rs.getInt("user_id"));
                            u.setUsername(rs.getString("username"));
                            u.setPassword(rs.getString("password"));
                            u.setPhone(rs.getString("phone"));
                            u.setAvatarUrl(rs.getString("avatar_url"));
                            u.setWechatOpenid(rs.getString("wechat_openid"));
                            u.setDataId(rs.getInt("data_id"));
                            return u;
                        }, username, password);
            } catch (Exception e) {
                return Result.error("账号或密码错误");
            }
        }

        if (user != null) {
            user.setPassword(null); // 安全起见，不返回密码
            return Result.success(user);
        }
        return Result.error("登录失败");
    }

    // ====================== 注册接口（保持原样） ======================
    @PostMapping("/register")
    public Result<String> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String avatarUrl,
            @RequestParam(required = false) String wechatOpenid
    ) {
        if (username.isEmpty() || password.isEmpty()) {
            return Result.error("用户名和密码不能为空");
        }

        try {
            // 检查用户名是否存在
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username = ?",
                    Integer.class, username);

            if (count > 0) {
                return Result.error("用户名已存在");
            }

            // 创建用户数据
            jdbcTemplate.update(
                    "INSERT INTO user_data (finished, mistake_numbers, correct_numbers, number_day) VALUES (0,0,0,0)"
            );

            Integer dataId = jdbcTemplate.queryForObject(
                    "SELECT currval('user_data_data_id_seq')", Integer.class);

            // 插入用户
            jdbcTemplate.update(
                    "INSERT INTO users (username, password, phone, avatar_url, wechat_openid, data_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    username, password, phone, avatarUrl, wechatOpenid, dataId);

            return Result.success("注册成功");

        } catch (Exception e) {
            return Result.error("注册失败：" + e.getMessage());
        }
    }
}

// ====================== 内部类：用于接收微信返回数据 ======================
class WechatSession {
    private String openid;
    private String session_key;
    private String unionid;
    private Integer errcode;
    private String errmsg;

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getSession_key() { return session_key; }
    public void setSession_key(String session_key) { this.session_key = session_key; }
    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }
    public Integer getErrcode() { return errcode; }
    public void setErrcode(Integer errcode) { this.errcode = errcode; }
    public String getErrmsg() { return errmsg; }
    public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
}