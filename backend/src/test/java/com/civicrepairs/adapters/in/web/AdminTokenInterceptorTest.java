package com.civicrepairs.adapters.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenInterceptorTest {

	private static boolean allowed(AdminTokenInterceptor interceptor, String token, MockHttpServletResponse response)
			throws Exception {
		var request = new MockHttpServletRequest("GET", "/api/admin/overview");
		if (token != null) {
			request.addHeader(AdminTokenInterceptor.HEADER, token);
		}
		return interceptor.preHandle(request, response, new Object());
	}

	@Test
	void onlyTheRightTokenPasses() throws Exception {
		var interceptor = new AdminTokenInterceptor("un-token-largo-de-admin");

		assertThat(allowed(interceptor, "un-token-largo-de-admin", new MockHttpServletResponse())).isTrue();
		var wrong = new MockHttpServletResponse();
		assertThat(allowed(interceptor, "otro", wrong)).isFalse();
		assertThat(wrong.getStatus()).isEqualTo(401);
		assertThat(allowed(interceptor, null, new MockHttpServletResponse())).isFalse();
	}

	@Test
	void withoutAdminTokenTheAdminApiDoesNotExist() throws Exception {
		var response = new MockHttpServletResponse();

		assertThat(allowed(new AdminTokenInterceptor(""), "", response)).isFalse();
		assertThat(response.getStatus()).isEqualTo(404);
	}
}
