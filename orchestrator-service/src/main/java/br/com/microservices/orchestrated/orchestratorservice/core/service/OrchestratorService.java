package br.com.microservices.orchestrated.orchestratorservice.core.service;


import br.com.microservices.orchestrated.orchestratorservice.core.dto.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.dto.History;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics;
import br.com.microservices.orchestrated.orchestratorservice.core.producer.SagaOrchestratorProducer;
import br.com.microservices.orchestrated.orchestratorservice.core.saga.SagaExecutionController;
import br.com.microservices.orchestrated.orchestratorservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static br.com.microservices.orchestrated.orchestratorservice.core.enums.EEventSource.ORCHESTRATOR;
import static br.com.microservices.orchestrated.orchestratorservice.core.enums.ESagaStatus.FAIL;
import static br.com.microservices.orchestrated.orchestratorservice.core.enums.ESagaStatus.SUCCESS;
import static br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics.NOTIFY_ENDING;

@Slf4j
@Service
@AllArgsConstructor
public class OrchestratorService {

  private final JsonUtil jsonUtil;
  private final SagaExecutionController controller;
  private final SagaOrchestratorProducer producer;

  public void startSaga(Event event) {
    event.setSource(ORCHESTRATOR);
    event.setStatus(SUCCESS);

    var topic = getTopics(event);
    log.info("SAGA STARTED!");
    addHistory(event, "Saga started!");
    sendToProduceWithTopic(event, topic);
  }

  public void finishSagaSuccess(Event event) {
    event.setSource(ORCHESTRATOR);
    event.setStatus(SUCCESS);

    log.info("SAGA FINISHED SUCCESSFULLY FOR EVENT {}", event.getId());
    addHistory(event, "Saga finished successfully!");
    notifyFinishedSaga(event);
  }

  public void finishSagaFail(Event event) {
    event.setSource(ORCHESTRATOR);
    event.setStatus(FAIL);

    log.info("SAGA FINISHED WITH ERRORS FOR EVENT {}", event.getId());
    addHistory(event, "Saga finished with errors!");
    notifyFinishedSaga(event);
  }

  public void continueSaga(Event event) {
    var topic = getTopics(event);
    log.info("SAGA CONTINUE FOR EVENT {}", event.getId());

    sendToProduceWithTopic(event, topic);
  }

  private ETopics getTopics(Event event) {
    return controller.getNextTopic(event);
  }

  private void addHistory(Event event, String message) {
    var history = History.builder()
            .source(event.getSource())
            .status(event.getStatus())
            .message(message)
            .createdAt(LocalDateTime.now())
            .build();

    event.addToHistory(history);
  }

  private void notifyFinishedSaga(Event event) {
    producer.sendEvent(NOTIFY_ENDING.getTopic(), jsonUtil.toJson(event));
  }

  private void sendToProduceWithTopic(Event event, ETopics topic) {
    producer.sendEvent(topic.getTopic(), jsonUtil.toJson(event));
  }
}
