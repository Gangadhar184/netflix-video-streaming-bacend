package com.example.videoservice;

import com.example.videoservice.event.VideoUploadedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(properties = "aws.s3.bucket-name=test-bucket")
class VideoserviceApplicationTests {

	@MockBean
	private S3Client s3Client;

	@MockBean
	private KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

	@Test
	void contextLoads() {
	}

}
