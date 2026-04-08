package com.example.mgdemoplus.utils;

import java.util.HashMap;
import java.util.Map;
//code对应业务码，message对应提示文案且与code对应，data对应数据

public class ResultUtil {
    private Boolean success;
    private Integer code;
    private String message;
    private Map<String, Object> data = new HashMap<>();

    // 然后是getter和setter方法
    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
//这里为啥要把构造方法私有呢，是为了限制ResultUtil的实例化，只能通过static方法来获取实例
    private ResultUtil() {

    }

    public static ResultUtil ok() {
        ResultUtil result = new ResultUtil();
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS);
        result.setMessage("成功");
        return result;
    }

    public static ResultUtil error() {
        ResultUtil result = new ResultUtil();
        result.setSuccess(false);
        result.setCode(ResultCode.ERROR);
        result.setMessage("失败");
        return result;
    }
    
    public static ResultUtil repeatUsername() {
        ResultUtil result = new ResultUtil();
        result.setSuccess(false);
        result.setCode(ResultCode.REPEAT_USERNAME);
        result.setMessage("用户名重复");
        return result;
    }

    public static ResultUtil sensitiveUsername() {
        ResultUtil result = new ResultUtil();
        result.setSuccess(false);
        result.setCode(ResultCode.SENSITIVE_USERNAME);
        result.setMessage("敏感词汇");
        return result;
    }
    //这个是动态的添加数据，比如：return ResultUtil.ok().data("message", "注册成功");
    public ResultUtil data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
    //这个是直接赋值一个map，比如：return ResultUtil.ok().data(Map<String, Object> data);
    public ResultUtil data(Map<String, Object> data) {
        this.setData(data);
        return this;
    }
}
