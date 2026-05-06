package org.example.entity;

public class User {
    private int userId;
    private String username;
    private String password;
    private String wechatOpenid;
    private String phone;
    private Integer dataId;
    private String avatarurl;  // 头像
    private String nickName;   // 昵称
    private String code;       // 微信code

    public User() {}

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getWechatOpenid() { return wechatOpenid; }
    public void setWechatOpenid(String wechatOpenid) { this.wechatOpenid = wechatOpenid; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getDataId() { return dataId; }
    public void setDataId(Integer dataId) { this.dataId = dataId; }

    public String getAvatarurl() { return avatarurl; }
    public void setAvatarurl(String avatarurl) { this.avatarurl = avatarurl; }

    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}