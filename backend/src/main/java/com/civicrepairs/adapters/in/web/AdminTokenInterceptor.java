package com.civicrepairs.adapters.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /api/admin/** with ADMIN_TOKEN, sent in the {@value #HEADER} header (never in the URL, so it stays out of
 * access logs). Without ADMIN_TOKEN the admin API does not exist (404).
 */
@Component
class AdminTokenInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	static final String HEADER = "X-Admin-Token";

	private final byte[] token;

	AdminTokenInterceptor(@Value("${civic.admin-token:}") String token) {
		this.token = token.strip().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/api/admin/**");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		if (token.length == 0) {
			return reject(response, HttpStatus.NOT_FOUND, "NOT_FOUND", "No encontramos ese recurso.");
		}
		String given = request.getHeader(HEADER);
		if (given == null || !MessageDigest.isEqual(token, given.strip().getBytes(StandardCharsets.UTF_8))) {
			return reject(response, HttpStatus.UNAUTHORIZED, "ADMIN_TOKEN_INVALID", "El código de administración no es válido.");
		}
		return true;
	}

	private static boolean reject(HttpServletResponse response, HttpStatus status, String code, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
		return false;
	}
}
