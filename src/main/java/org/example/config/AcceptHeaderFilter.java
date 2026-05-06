package org.example.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AcceptHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        request = new HttpServletRequestWrapper(httpRequest) {
            @Override
            public String getHeader(String name) {
                if ("Accept".equalsIgnoreCase(name)) {
                    return "application/json, */*";
                }
                return super.getHeader(name);
            }
        };

        chain.doFilter(request, response);
    }
}
