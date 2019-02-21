package io.github.malczuuu.iemu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malczuuu.iemu.common.ObjectMapperFactory;
import io.github.malczuuu.iemu.common.config.Config;
import io.github.malczuuu.iemu.common.config.ConfigReader;
import io.github.malczuuu.iemu.domain.StateService;
import io.github.malczuuu.iemu.domain.StateServiceFactory;
import io.github.malczuuu.iemu.http.WebSocketEvent;
import io.github.malczuuu.iemu.http.WebSocketService;
import io.github.malczuuu.iemu.http.WebSocketServiceFactory;
import io.github.malczuuu.iemu.starter.HttpServerStarter;
import io.github.malczuuu.iemu.starter.LwM2mClientStarter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class App {

  public static void main(String[] args) {
    new App(new ConfigReader(new ObjectMapperFactory().getYamlObjectMapper()).readConfig()).run();
  }

  private final WebSocketService webSocketService = new WebSocketServiceFactory().create();
  private final StateService stateService = new StateServiceFactory().getStateService();

  private final ObjectMapper mapper = new ObjectMapperFactory().getJsonObjectMapper();
  private final Config config;

  private final Runnable runnable =
      () -> {
        try {
          webSocketService.sendMessage(
              mapper.writeValueAsString(new WebSocketEvent("state", stateService.getState())));
        } catch (JsonProcessingException e) {
          // Ignored
        }
      };

  private App(Config config) {
    this.config = config;
  }

  private void run() {
    Runtime.getRuntime().addShutdownHook(new Thread(stateService::shutdown));

    stateService.subscribeOnCurrentTimeChange(ignored -> runnable.run());
    stateService.subscribeOnStateChange(ignored -> runnable.run());
    stateService.subscribeOnTimeCounterChange(ignored -> runnable.run());
    stateService.subscribeOnDimmerChange(ignored -> runnable.run());

    if (config.getLwM2mConfig().isEnabled()) {
      new LwM2mClientStarter(config.getLwM2mConfig(), stateService).run();
    }
    new HttpServerStarter(config.getHttpConfig(), webSocketService, stateService, mapper).run();
  }
}
