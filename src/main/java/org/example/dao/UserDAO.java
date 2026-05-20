package org.example.dao;

import org.example.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 根据用户名和密码查询（用于账号密码登录）
     */
    public User findByUsernameAndPassword(String username, String password) {
        // 注意：SQL 中的列名必须和数据库一致，BeanPropertyRowMapper 会自动映射到 User 对象
        String sql = "SELECT user_id, username, password, phone, avatar_url, wechat_openid, data_id " +
                "FROM users WHERE username = ? AND password = ?";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new BeanPropertyRowMapper<>(User.class),
                    username,
                    password
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 根据用户名查询（用于注册检查）
     */
    public User findByUsername(String username) {
        String sql = "SELECT user_id, username FROM users WHERE username = ?";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new BeanPropertyRowMapper<>(User.class),
                    username
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 根据微信 OpenID 查询（用于微信登录）
     */
    public User findByWechatOpenid(String wechatOpenid) {
        String sql = "SELECT user_id, username, password, phone, avatar_url, wechat_openid, data_id " +
                "FROM users WHERE wechat_openid = ?";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new BeanPropertyRowMapper<>(User.class),
                    wechatOpenid
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 保存用户（用于微信自动注册）
     */
    public void saveUser(User user) {
        // 1. 处理用户名
        String finalUsername = user.getUsername();
        if (finalUsername == null || finalUsername.isEmpty()) {
            finalUsername = "微信用户_" + (int)(Math.random() * 10000);
        }

        // 2. 处理密码
        String finalPassword = user.getPassword();
        if (finalPassword == null || finalPassword.isEmpty()) {
            finalPassword = "123456"; // 微信登录默认密码
        }

        // 3. 处理头像
        // 注意：这里调用的是 getAvatarurl()，因为你的 User 类里字段名是 avatarurl
        String finalAvatar = user.getAvatarurl();
        if (finalAvatar == null || finalAvatar.isEmpty()) {
            finalAvatar = ""; // 如果微信没返回头像，存空字符串
        }

        // 4. 构造 SQL
        // 注意：去掉了 create_time，因为你的表里没有这个字段
        // 注意：增加了 data_id 字段，因为你的表里有这个字段
        // 假设注册时 data_id 默认为 NULL 或者你需要传入一个具体的 ID
        String sql = "INSERT INTO users (username, password, wechat_openid, avatar_url, phone, data_id) VALUES (?, ?, ?, ?, ?, ?)";

        // 5. 执行插入
        // 如果 data_id 是自增或允许为 null，这里可以传 null
        jdbcTemplate.update(sql,
                finalUsername,
                finalPassword,
                user.getWechatOpenid(),
                finalAvatar,
                user.getPhone(),
                user.getDataId() // 如果注册时没有 data_id，这里传 null 即可
        );
    }
}