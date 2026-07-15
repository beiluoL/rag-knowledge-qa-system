package com.example.ragkb.util;

import com.example.ragkb.exception.BusinessException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF 基础防护：在发起对外 HTTP 请求前校验目标地址，
 * 拒绝非 http/https 协议、无法解析、或解析到内网/私有/保留地址的请求。
 *
 * 注意：仅做解析阶段校验，可拦截绝大多数内网探测（localhost、10/8、192.168/16、
 * 169.254/16、172.16/12、回环、链路本地、组播/保留段）。完整防护还需在连接层
 * 对最终连接 IP 二次校验（防 DNS rebinding），此处为主要防线。
 */
public final class SsrfGuard {

    private SsrfGuard() {}

    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(400, "URL 不能为空");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(400, "仅支持 http/https 协议的链接");
        }
        String host;
        try {
            host = new URI(url).getHost();
        } catch (Exception e) {
            throw new BusinessException(400, "URL 格式不合法");
        }
        if (host == null || host.isBlank()) {
            throw new BusinessException(400, "URL 缺少有效主机名");
        }
        // 去除 IPv6 方括号
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessException(400, "无法解析该主机");
        }
        if (addresses.length == 0) {
            throw new BusinessException(400, "无法解析该主机");
        }
        for (InetAddress addr : addresses) {
            if (isPrivateOrReserved(addr)) {
                throw new BusinessException(400, "禁止访问内网或保留地址");
            }
        }
    }

    private static boolean isPrivateOrReserved(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
            return true;
        }
        if (addr instanceof Inet6Address) {
            byte[] b = addr.getAddress();
            // 唯一本地地址 fc00::/7
            return (b[0] & 0xfe) == 0xfc;
        }
        if (addr instanceof Inet4Address) {
            byte[] b = addr.getAddress();
            int a = b[0] & 0xff;
            if (a == 0) return true;          // 0.0.0.0/8
            if (a == 10) return true;         // 10.0.0.0/8
            if (a == 127) return true;        // 127.0.0.0/8 回环
            if (a == 169 && (b[1] & 0xff) == 254) return true; // 169.254.0.0/16 链路本地
            if (a == 172 && (b[1] & 0xff) >= 16 && (b[1] & 0xff) <= 31) return true; // 172.16.0.0/12
            if (a == 192 && (b[1] & 0xff) == 168) return true; // 192.168.0.0/16
            if (a >= 224) return true;        // 224.0.0.0/4 组播及保留
        }
        return false;
    }
}
