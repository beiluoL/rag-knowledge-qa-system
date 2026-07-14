package com.example.ragkb.sandbox;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 代码沙箱子进程入口。
 * <p>
 * 由 CodeSandboxService 以独立 JVM 进程启动（进程隔离）：
 *   java -cp &lt;tempDir&gt;:&lt;appClasses&gt; com.example.ragkb.sandbox.Harness &lt;cases.json&gt; &lt;Solution.java&gt;
 * 职责：
 *   1. 用 javax.tools 编译用户提交的 Solution.java；
 *   2. 通过反射按函数名 + 参数个数定位方法并调用；
 *   3. 捕获用户 System.out 作为日志；
 *   4. 向标准输出打印唯一一行 JSON 结果（错误走标准错误）。
 * </p>
 */
public final class Harness {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        PrintStream orig = System.out;
        // 捕获用户代码的 System.out 输出，避免污染结果 JSON
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream cap = new PrintStream(buf, true, StandardCharsets.UTF_8);
        System.setOut(cap);
        try {
            String casesPath = args[0];
            String solutionPath = args[1];
            String casesText = new String(Files.readAllBytes(Paths.get(casesPath)), StandardCharsets.UTF_8);
            Map<String, Object> root = (Map<String, Object>) parse(casesText);
            String fn = (String) root.get("functionName");
            List<Object> cases = (List<Object>) root.get("cases");

            File solFile = new File(solutionPath);
            File outDir = solFile.getParentFile();
            // 可选第 3 参数 "skip"：父进程已通过编译缓存复用 Solution.class，跳过本子进程内编译
            boolean skipCompile = args.length > 2 && "skip".equals(args[2]);

            if (!skipCompile) {
                // 1) 编译
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                if (compiler == null) {
                    System.setOut(orig);
                    orig.println(errResult("当前运行环境未提供 Java 编译器（需要 JDK 而非 JRE）。"));
                    orig.flush();
                    return;
                }
                DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
                StandardJavaFileManager fm = compiler.getStandardFileManager(diags, Locale.US, StandardCharsets.UTF_8);
                Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(solFile);
                fm.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(outDir));
                boolean compiled = compiler.getTask(null, fm, diags, null, null, units).call();
                fm.close();
                if (!compiled) {
                    StringBuilder sb = new StringBuilder("编译错误：\n");
                    for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                        sb.append("  第 ").append(d.getLineNumber()).append(" 行：")
                          .append(d.getMessage(Locale.US)).append('\n');
                    }
                    System.setOut(orig);
                    orig.println(errResult(sb.toString().trim()));
                    orig.flush();
                    return;
                }
            }

            // 2) 加载并反射调用
            try (URLClassLoader cl = new URLClassLoader(new URL[]{ outDir.toURI().toURL() })) {
                Class<?> sol = cl.loadClass("Solution");
                Object inst = sol.getDeclaredConstructor().newInstance();

                int arity = ((List<Object>) ((Map<String, Object>) cases.get(0)).get("input")).size();
                Method m = null;
                for (Method mm : sol.getMethods()) {
                    if (mm.getName().equals(fn) && mm.getParameterCount() == arity) { m = mm; break; }
                }
                if (m == null) {
                    System.setOut(orig);
                    orig.println(errResult("未找到函数 " + fn + "（或参数个数不匹配）。"));
                    orig.flush();
                    return;
                }
                Class<?>[] pts = m.getParameterTypes();

                List<Map<String, Object>> results = new ArrayList<>();
                for (Object co : cases) {
                    Map<String, Object> c = (Map<String, Object>) co;
                    List<Object> input = (List<Object>) c.get("input");
                    Object[] invokeArgs = new Object[pts.length];
                    for (int k = 0; k < pts.length; k++) invokeArgs[k] = convert(input.get(k), pts[k]);
                    long start = System.nanoTime();
                    Object got = m.invoke(inst, invokeArgs);
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    Object expected = c.get("expected");
                    boolean pass = toJson(got).equals(toJson(expected));
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("ms", ms);
                    r.put("pass", pass);
                    r.put("got", parse(toJson(got))); // 还原为 JSON 原生值，便于后端反序列化
                    results.add(r);
                }

                String logs = buf.toString();
                System.setOut(orig);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("success", true);
                out.put("error", null);
                out.put("results", results);
                out.put("logs", logs.isEmpty() ? Collections.emptyList() : Arrays.asList(logs.split("\n")));
                orig.println(toJson(out));
                orig.flush();
            }
        } catch (Throwable t) {
            String logs = buf.toString();
            System.setOut(orig);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", false);
            out.put("error", t.getMessage() != null ? t.getMessage() : t.getClass().getName());
            out.put("results", Collections.emptyList());
            out.put("logs", logs.isEmpty() ? Collections.emptyList() : Arrays.asList(logs.split("\n")));
            orig.println(toJson(out));
            orig.flush();
        }
    }

    // ───────────────────────── 类型转换：JSON 值 → 方法实参 ─────────────────────────
    private static Object convert(Object jv, Class<?> t) {
        if (t == int.class || t == Integer.class) return ((Number) jv).intValue();
        if (t == long.class || t == Long.class) return ((Number) jv).longValue();
        if (t == double.class || t == Double.class) return ((Number) jv).doubleValue();
        if (t == float.class || t == Float.class) return ((Number) jv).floatValue();
        if (t == short.class || t == Short.class) return ((Number) jv).shortValue();
        if (t == byte.class || t == Byte.class) return ((Number) jv).byteValue();
        if (t == boolean.class || t == Boolean.class) return Boolean.TRUE.equals(jv);
        if (t == char.class || t == Character.class) return ((String) jv).charAt(0);
        if (t == String.class) return (String) jv;
        if (t == int[].class) return toIntArray(jv);
        if (t == long[].class) return toLongArray(jv);
        if (t == double[].class) return toDoubleArray(jv);
        if (t == String[].class) return toStringArray(jv);
        if (t == List.class || t == Object.class) return jv;
        return jv;
    }

    private static int[] toIntArray(Object jv) {
        List<?> l = (List<?>) jv; int[] a = new int[l.size()];
        for (int i = 0; i < l.size(); i++) a[i] = ((Number) l.get(i)).intValue();
        return a;
    }
    private static long[] toLongArray(Object jv) {
        List<?> l = (List<?>) jv; long[] a = new long[l.size()];
        for (int i = 0; i < l.size(); i++) a[i] = ((Number) l.get(i)).longValue();
        return a;
    }
    private static double[] toDoubleArray(Object jv) {
        List<?> l = (List<?>) jv; double[] a = new double[l.size()];
        for (int i = 0; i < l.size(); i++) a[i] = ((Number) l.get(i)).doubleValue();
        return a;
    }
    @SuppressWarnings("unchecked")
    private static String[] toStringArray(Object jv) {
        List<Object> l = (List<Object>) jv; String[] a = new String[l.size()];
        for (int i = 0; i < l.size(); i++) a[i] = String.valueOf(l.get(i));
        return a;
    }

    private static String errResult(String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", false);
        out.put("error", msg);
        out.put("results", Collections.emptyList());
        out.put("logs", Collections.emptyList());
        return toJson(out);
    }

    // ───────────────────────── 极简 JSON 解析（够用即可，避免引入依赖） ─────────────────────────
    private static Object parse(String s) { return new Parser(s).value(); }

    private static final class Parser {
        final String s; int i;
        Parser(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (c == 't') { i += 4; return Boolean.TRUE; }
            if (c == 'f') { i += 5; return Boolean.FALSE; }
            if (c == 'n') { i += 4; return null; }
            return num();
        }
        Map<String, Object> obj() {
            i++; Map<String, Object> m = new LinkedHashMap<>();
            ws(); if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws(); String k = str(); ws(); i++; // 跳过 ':'
                Object v = value(); m.put(k, v); ws();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
            }
            return m;
        }
        List<Object> arr() {
            i++; List<Object> a = new ArrayList<>();
            ws(); if (s.charAt(i) == ']') { i++; return a; }
            while (true) {
                a.add(value()); ws();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
            }
            return a;
        }
        String str() {
            i++; StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i);
                if (c == '"') { i++; break; }
                if (c == '\\') {
                    i++; char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(e);
                    }
                } else { sb.append(c); i++; }
            }
            return sb.toString();
        }
        Object num() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ("0123456789+-.eE".indexOf(c) >= 0) i++; else break;
            }
            String n = s.substring(start, i);
            if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) return Double.parseDouble(n);
            try { return Long.parseLong(n); } catch (Exception e) { return Double.parseDouble(n); }
        }
    }

    // ───────────────────────── 规范 JSON 序列化（用于比较与输出） ─────────────────────────
    private static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        emit(o, sb);
        return sb.toString();
    }
    private static void emit(Object o, StringBuilder sb) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String) { sb.append('"'); esc((String) o, sb); sb.append('"'); return; }
        if (o instanceof Boolean) { sb.append(((Boolean) o) ? "true" : "false"); return; }
        if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) { sb.append(o.toString()); return; }
        if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) sb.append(Long.toString((long) d));
            else sb.append(Double.toString(d));
            return;
        }
        if (o.getClass().isArray()) {
            sb.append('[');
            int len = java.lang.reflect.Array.getLength(o);
            for (int k = 0; k < len; k++) { if (k > 0) sb.append(','); emit(java.lang.reflect.Array.get(o, k), sb); }
            sb.append(']'); return;
        }
        if (o instanceof List) {
            sb.append('[');
            List<?> l = (List<?>) o;
            for (int k = 0; k < l.size(); k++) { if (k > 0) sb.append(','); emit(l.get(k), sb); }
            sb.append(']'); return;
        }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"'); esc(String.valueOf(e.getKey()), sb); sb.append("\":");
                emit(e.getValue(), sb);
            }
            sb.append('}'); return;
        }
        sb.append('"'); esc(String.valueOf(o), sb); sb.append('"');
    }
    private static void esc(String s, StringBuilder sb) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
    }
}
