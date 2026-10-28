package com.civicrepairs.adapters.out.google;

import com.civicrepairs.application.port.out.GoogleIdentityPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** GOOGLE_CLIENT_ID not set: every token is rejected and the web hides the button. */
@Component
@ConditionalOnExpression("'${civic.google.client-id:}'.isBlank()")
class DisabledGoogleIdentity implements GoogleIdentityPort {

	@Override
	public Optional<GoogleIdentity> verify(String idToken) {
		return Optional.empty();
	}
}
