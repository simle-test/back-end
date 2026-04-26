package org.example.dao;

import org.example.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public User findByUsernameAndPassword(String username, String password) {
        String sql = "SELECT user_id, username, password, phone, avatar_url, wechat_openid, " +
                "finished, mistake_numbers, correct_numbers, number_day, mistake_list_id, correct_lis_id " +
                "FROM users WHERE username = ? AND password = ?";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new BeanPropertyRowMapper<>(User.class),
                    username,
                    password
            );
        } catch (Exception e) {
            return null;
        }
    }

    public User findByUsername(String username) {
        String sql = "SELECT user_id, username FROM users WHERE username = ?";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new BeanPropertyRowMapper<>(User.class),
                    username
            );
        } catch (Exception e) {
            return null;
        }

    }// 微信登录用
    // 微信登录查询
    public User findByWechatOpenid(String wechatOpenid) {
        String sql = "SELECT * FROM users WHERE wechat_openid = ?";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new BeanPropertyRowMapper<>(User.class),
                    wechatOpenid
            );
        } catch (Exception e) {
            return null;
        }
    }

    // 自动注册/保存用户
    // UserDAO.java

    public void saveUser(User user) {
        // 1. 处理必填字段（防止微信登录时 username 为空导致报错）
        // 如果前端没传 username，后端自动生成一个
        String finalUsername = user.getUsername();
        if (finalUsername == null || finalUsername.isEmpty()) {
            finalUsername = "微信用户_" + (int)(Math.random() * 10000);
        }

        // 2. 处理密码字段（如果数据库 password 字段设为 NOT NULL）
        // 微信登录不需要密码，这里给个默认值
        String finalPassword = user.getPassword();
        if (finalPassword == null || finalPassword.isEmpty()) {
            finalPassword = "123456"; // 默认密码
        }

        // 3. 构造 SQL
        // 注意：根据你的截图，主键是 user_id，头像字段是 avatar_url
        // 假设数据库里还有 create_time 字段记录创建时间，如果没有请去掉 NOW() 和对应的 ?
        String sql = "INSERT INTO users (username, password, wechat_openid, avatar_url, create_time) VALUES (?, ?, ?, ?, NOW())";

        // 4. 执行插入
        jdbcTemplate.update(sql,
                finalUsername,      // 对应第一个 ?
                finalPassword,      // 对应第二个 ?
                user.getWechatOpenid(), // 对应第三个 ?
                user.getAvatarUrl() // 对应第四个 ? (如果是 null 数据库需允许 null，或者这里给个默认图片地址)
        );
    }
}