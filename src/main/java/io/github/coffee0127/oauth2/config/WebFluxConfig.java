package io.github.coffee0127.oauth2.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
@AllArgsConstructor
public class WebFluxConfig implements WebFluxConfigurer {

  private final ObjectMapper objectMapper;

  @Override
  public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
  }
}
