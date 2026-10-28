package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.SessionUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Optional;

/**
 * Injects the signed-in citizen into controller methods. A parameter of type {@link AuthenticatedUser} requires a
 * session (401 otherwise); {@code Optional<AuthenticatedUser>} is empty for anonymous visitors.
 */
@Component
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

	private final SessionUseCase sessions;

	CurrentUserArgumentResolver(SessionUseCase sessions) {
		this.sessions = sessions;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(this);
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == AuthenticatedUser.class || isOptionalUser(parameter);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest webRequest,
			WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		Optional<AuthenticatedUser> user = SessionCookies.read(request).flatMap(sessions::resolve);
		if (isOptionalUser(parameter)) {
			return user;
		}
		return user.orElseThrow(() -> new AccountException(Code.UNAUTHENTICATED, "Inicia sesión para continuar."));
	}

	private static boolean isOptionalUser(MethodParameter parameter) {
		return parameter.getParameterType() == Optional.class
				&& parameter.nestedIfOptional().getNestedParameterType() == AuthenticatedUser.class;
	}
}
