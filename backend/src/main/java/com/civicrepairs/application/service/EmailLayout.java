package com.civicrepairs.application.service;

/** The one HTML layout of every email: a title, a paragraph, a button and a footer. */
final class EmailLayout {

	private EmailLayout() {
	}

	/** @param intro already escaped (may contain markup) */
	static String html(String title, String intro, String link, String action, String footer) {
		return """
				<div style="font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;color:#101828">
				  <h2 style="font-size:20px">%s</h2>
				  <p>%s</p>
				  <p><a href="%s" style="display:inline-block;padding:10px 16px;background:#b42318;color:#fff;\
				text-decoration:none;border-radius:8px;font-weight:600">%s</a></p>
				  <p style="color:#475467;font-size:14px">%s</p>
				  <p style="color:#667085;font-size:12px">Si el botón no funciona, copia este enlace:<br>%s</p>
				</div>
				""".formatted(escape(title), intro, link, escape(action), escape(footer), link);
	}

	static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
