package com.redhat;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ProducerRoute extends RouteBuilder {
	@Override
	public void configure() throws Exception {
		restConfiguration().component("servlet").bindingMode(RestBindingMode.json);

		KafkaComponent kafka = new KafkaComponent();
		KafkaConfiguration kafkaConfig = new KafkaConfiguration();
		kafkaConfig.setBrokers("my-cluster-kafka-external-bootstrap:9094");
		kafkaConfig.setSecurityProtocol("SSL");
		kafkaConfig.setSslTruststoreLocation("/tmp/src/src/main/resources/keystore.jks");
		kafkaConfig.setSslTruststorePassword("password");
		kafka.setConfiguration(kafkaConfig);

		getContext().addComponent("kafka", kafka);

		rest("/rest")
			.post("/produce/{color}").route().streamCaching().process(new Processor() {
				@Override
				public void process(Exchange exchange) throws Exception {
					Message message = exchange.getIn();
					log.info("Received message: {}", message.getBody(String.class));
	
					message.setBody(new ObjectMapper().writeValueAsString(exchange.getIn().getBody()));
					message.setHeader(KafkaConstants.PARTITION_KEY, 0);
					message.setHeader(KafkaConstants.KEY, "Camel");
				}
			}).recipientList(simple("kafka:directive-${header.color}")).setBody(constant("Message sent successfully.")).endRest()
			
			//killswitch /camel/rest/gameOver/{color}
			.get("/gameOver/{color}").route().setBody(simple("${header.color}")).to("kafka:game-over");
		}
}
