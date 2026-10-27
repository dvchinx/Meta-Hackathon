package com.civicrepairs;

import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.VisionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production wiring: the real Muse and vision adapters must start. Nothing is called (dummy key, unreachable URL),
 * so no credits are spent; this only catches missing beans the fake-based tests cannot see.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"META_API_ENABLED=true",
		"META_API_KEY=dummy-key-never-used",
		"VISION_SERVICE_URL=http://127.0.0.1:9",
})
class RealAdaptersWiringTest {

	@Autowired
	LanguageModelPort languageModel;
	@Autowired
	VisionPort vision;

	@Test
	void realAdaptersAreWired() {
		assertThat(languageModel.getClass().getSimpleName()).isEqualTo("MuseLanguageModelAdapter");
		assertThat(vision.getClass().getSimpleName()).isEqualTo("VisionServiceAdapter");
	}
}
