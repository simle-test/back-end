package org.example.common;

import lombok.Data; // 如果你用了 Lombok，否则请手动写 Getter/Setter

@Data
public class Result<T> {
    private Integer code; // 状态码：200 成功，500 失败
    private String message; // 提示信息
    private T data; // 具体的数据

    // 成功返回
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "操作成功";
        result.data = data;
        return result;
    }

    // 失败返回
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.code = 500;
        result.message = message;
        return result;
    }
}