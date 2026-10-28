package com.civicrepairs.application.port.in;

/**
 * The data policy (Ley 1581 de 2012) citizens accept before their filing data is stored or shared. The text lives in
 * the web app ({@code web/src/privacy/policy.ts}, shown at #/privacidad), which sends back the version it showed.
 * Change both together and bump the version so citizens accept it again.
 */
public final class PrivacyPolicy {

	public static final String CURRENT_VERSION = "2026-10-27";

	private PrivacyPolicy() {
	}
}
