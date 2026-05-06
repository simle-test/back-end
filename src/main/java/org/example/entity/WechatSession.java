package org.example.entity;

import org.apache.ibatis.type.Alias; // 加这个导入

@Alias("WechatSession2") // 就加这一行！
public class WechatSession {
    private String openid;
    private String session_key;
    private String unionid;
    private Integer errcode;
    private String errmsg;

    // 必须有 Getter 和 Setter
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