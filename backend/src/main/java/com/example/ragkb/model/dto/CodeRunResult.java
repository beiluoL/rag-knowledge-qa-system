package com.example.ragkb.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 代码沙箱执行结果。results 每一项对应一个测试用例的运行结果，
 * got 为后端反射调用后的真实返回值（JSON 反序列化后的原生类型）。
 */
@Data
public class CodeRunResult {
    private boolean success;
    private String error;
    private List<CaseOutcome> results;
    private List<String> logs;

    @Data
    public static class CaseOutcome {
        private long ms;
        private boolean pass;
        private Object got;
    }
}
