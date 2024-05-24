package br.com.microservices.orchestrated.productvalidationservice.core.service;

import br.com.microservices.orchestrated.productvalidationservice.core.dto.Event;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.History;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.OrderProduct;
import br.com.microservices.orchestrated.productvalidationservice.core.model.Validation;
import br.com.microservices.orchestrated.productvalidationservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ProductRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ValidationRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.utils.JsonUtil;
import jakarta.validation.ValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import static br.com.microservices.orchestrated.productvalidationservice.core.enums.ESagaStatus.*;

@Slf4j
@Service
@AllArgsConstructor
public class ProductValidationService {

  private static final String CURRENT_SOURCE = "PRODUCT_VALIDATION_SERVICE";

  private final JsonUtil jsonUtil;
  private final KafkaProducer producer;
  private final ProductRepository productRepository;
  private final ValidationRepository validationRepository;

  public void validateExistingProducts(Event event) {
    try {
      checkCurrentValidation(event);
      createValidation(event, true);
      handleSuccess(event);
    } catch (RuntimeException ex) {
      log.error("Error trying to validate products: ", ex);
      handleFailCurrentNotExecuted(event, ex.getMessage());
    }
    producer.sendEvent(jsonUtil.toJson(event));
  }

  private void checkCurrentValidation(Event event) {
    validateProductsInformed(event);
    if (validationRepository.existsByOrderIdAndTransactionId(
            event.getOrderId(),
            event.getTransactionId()
    )) {
      throw  new ValidationException("There's another transactionId for this validation.");
    }

    event.getPayload().getProducts().forEach(product -> {
      validateProductInformed(product);
      validateExistsProduct(product.getProduct().getCode());
    });
  }

  private void validateProductsInformed(Event event ) {
    if(ObjectUtils.isEmpty(event.getPayload()) || ObjectUtils.isEmpty(event.getPayload().getProducts())) {
      throw new ValidationException("Products List is empty!");
    }

    if(ObjectUtils.isEmpty(event.getPayload().getId()) || ObjectUtils.isEmpty(event.getPayload().getTransactionId())) {
      throw new ValidationException("OrderID and TransactionID must be informed!");
    }
  }

  private void validateProductInformed(OrderProduct orderProduct) {
    if(ObjectUtils.isEmpty(orderProduct.getProduct()) || ObjectUtils.isEmpty(orderProduct.getProduct().getCode())) {
      throw new ValidationException("Product must be informed");
    }
  }

  private void validateExistsProduct(String code) {
    if(!productRepository.existsByCode(code)) {
      throw new ValidationException("Product does not exist in database");
    }
  }

  private void createValidation(Event event, boolean success) {
    var validation = Validation.builder()
            .orderId(event.getPayload().getId())
            .transactionId(event.getTransactionId())
            .success(success)
            .build();
    validationRepository.save(validation);
  }

  private void handleSuccess(Event event) {
    event.setStatus(SUCCESS);
    event.setSource(CURRENT_SOURCE);
    addHistory(event, "Products are validated successfully!");
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

  private void handleFailCurrentNotExecuted(Event event, String message) {
    event.setStatus(ROLLBACK_PENDING);
    event.setSource(CURRENT_SOURCE);
    addHistory(event, "Fail to validate products: ".concat(message));
  }

  public void rollbackEvent(Event event) {
    changeValidationToFail(event);
    event.setStatus(FAIL);
    event.setSource(CURRENT_SOURCE);
    addHistory(event, "Rollback executed on product validation!");
    producer.sendEvent(jsonUtil.toJson(event));
  }

  private void changeValidationToFail(Event event) {
    Consumer<Validation> presentFn = (validation) -> {
      validation.setSuccess(false);
      validationRepository.save(validation);
    };

    Runnable createValidationFailFn = () ->
            createValidation(event, false);

    validationRepository.findByOrderIdAndTransactionId(
            event.getPayload().getId(),
            event.getTransactionId()
    ).ifPresentOrElse(
            presentFn,
            createValidationFailFn
    );

  }
}
