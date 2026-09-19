package org.dromara.cmd.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CMD POC 请求日志过滤器。
 * <p>
 * 打印所有 /cmd/** 请求的 Method、URI、QueryString、响应状态与耗时，便于联调时
 * 在后台控制台直接查看前端调用了哪些接口。开关由 application.yml 的
 * {@code cmd.poc.log-requests} 控制（默认开启），运维可自由关闭。
 *
 * @author Essilor CMD POC
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(prefix = "cmd.poc", name = "log-requests", havingValue = "true", matchIfMissing = true)
public class CmdRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("CMD_REQUEST");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String url = query == null || query.isEmpty() ? uri : uri + "?" + query;
        // 步骤日志①：请求进入（含线程名，便于排查并发 / 异步链路）
        log.info("[CMD][REQ ] 线程={} {} {}", Thread.currentThread().getName(), method, url);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            // 步骤日志②：请求结束（状态 + 耗时）
            log.info("[CMD][RES ] 线程={} {} {} -> {} ({} ms)",
                Thread.currentThread().getName(), method, url, response.getStatus(), cost);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/cmd/");
    }
}
