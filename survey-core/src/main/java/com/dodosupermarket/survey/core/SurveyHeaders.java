package com.dodosupermarket.survey.core;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 问卷相关接口与页面的安全响应头过滤器。
 * 禁止缓存与 Referrer 泄漏、防 MIME 嗅探，并将请求体限制在 256KB 以内（防大包攻击）。
 */
@Configuration
public class SurveyHeaders {
    /** 请求体上限：超过即拒绝，避免大包直接进堆内存 */
    private static final long MAX_BODY_BYTES = 256 * 1024L;

    /**
     * 注册过滤器，作用于 /api/survey/* 与 /survey/*，优先级高于业务过滤器
     */
    @Bean
    public FilterRegistrationBean<Filter> surveyPrivacyHeaders() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletRequest http = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setHeader("Cache-Control", "private, no-store");
                httpResponse.setHeader("Referrer-Policy", "no-referrer");
                httpResponse.setHeader("X-Content-Type-Options", "nosniff");
                if (http.getContentLengthLong() > MAX_BODY_BYTES) {
                    httpResponse.sendError(413);
                    return;
                }
                try {
                    // chunked 请求没有 Content-Length，必须在读取时限量，否则大包会绕过上面的判断
                    chain.doFilter(new LimitedBodyRequest(http, MAX_BODY_BYTES), httpResponse);
                } catch (Exception ex) {
                    if (containsPayloadTooLarge(ex)) {
                        if (!httpResponse.isCommitted()) httpResponse.sendError(413);
                        return;
                    }
                    throw ex;
                }
            }
        });
        bean.addUrlPatterns("/api/survey/*", "/survey/*");
        bean.setOrder(-101);
        return bean;
    }

    private static boolean containsPayloadTooLarge(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof PayloadTooLargeException) return true;
            if (current == current.getCause()) break;
        }
        return false;
    }

    /**
     * 请求体超限标记：作为 IOException 抛出，可被 Spring 包装成"请求体不可读"，
     * 同时保留类型以便过滤器转换成 413。
     */
    static class PayloadTooLargeException extends IOException {
        PayloadTooLargeException() {
            super("survey request body exceeds " + MAX_BODY_BYTES + " bytes");
        }
    }

    /**
     * 读取时限制请求体大小的请求包装。
     */
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new LimitedServletInputStream(super.getInputStream(), limit);
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding == null ? "UTF-8" : encoding));
        }
    }

    /**
     * 超过上限即抛出的输入流包装。
     */
    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        LimitedServletInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0 && ++count > limit) throw new PayloadTooLargeException();
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count += read;
                if (count > limit) throw new PayloadTooLargeException();
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
