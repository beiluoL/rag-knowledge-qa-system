package com.example.ragkb.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 代码沙箱执行请求：前端提交用户代码 + 函数名 + 测试用例。
 * testCases.input / expected 用 Object 以兼容多语言多类型（数字、字符串、数组等）。
 */
@Data
public class CodeRunRequest {
    /** 用户编写的方法体（不含 class 声明，后端会包一层 Solution 类） */
    private String code;
    /** 待调用的函数名，如 twoSum / reverseString */
    private String functionName;
    /** 测试用例列表 */
    private List<TestCase> testCases;

    @Data
    public static class TestCase {
        /** 入参列表，如 [[2,7,11,15], 9] 或 ["hello"] */
        private List<Object> input;
        /** 期望返回值，如 [0,1] 或 "olleh" */
        private Object expected;
    }
}
