package org.example.entity;

public class User {
    private int userId;
    private String username;
    private String password;
    private String wechatOpenid;
    private String avatarUrl;
    private String phone;
    private Integer dataId;

    public User() {}

    public User(int userId, String username, String password, String wechatOpenid, String avatarUrl, String phone, Integer dataId) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.wechatOpenid = wechatOpenid;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.dataId = dataId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getWechatOpenid() {
        return wechatOpenid;
    }

    public void setWechatOpenid(String wechatOpenid) {
        this.wechatOpenid = wechatOpenid;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getDataId() {
        return dataId;
    }

    public void setDataId(Integer dataId) {
        this.dataId = dataId;
    }
}