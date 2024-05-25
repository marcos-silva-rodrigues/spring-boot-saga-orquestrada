package br.com.microservices.orchestrated.paymentservice.core.service;

import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.History;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProduct;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus.*;
import static br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus.*;
import static br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus.SUCCESS;

@Slf4j
@Component
@AllArgsConstructor
public class PaymentService {

  private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
  private static final Double REDUCE_SUM_VALUE = 0.0;
  private static final Double MIN_AMOUNT_VALUE = 0.1;

  private final KafkaProducer producer;
  private final JsonUtil jsonUtil;
  private final PaymentRepository repository;

  public void realizePayment(Event event) {
    try {
      checkCurrentValidation((event));
      createPendingPayment(event);
      var payment = findByOrderIdAndTransactionId(event);
      validateAmount(payment.getTotalAmount());
      changePaymentToSuccess(payment);
      handleSuccess(event);
    } catch (Exception ex) {
      log.error("Error trying to make payment: ".concat(ex.getMessage()));
      handleFailCurrentNotExecuted(event, ex.getMessage());
    }

    producer.sendEvent(jsonUtil.toJson(event));
  }

  private void handleFailCurrentNotExecuted(Event event, String message) {
    event.setStatus(ROLLBACK_PENDING);
    event.setSource(CURRENT_SOURCE);
    addHistory(event, "Fail to realize payment: ".concat(message));
  }

  private void checkCurrentValidation(Event event) {
    if (repository.existsByOrderIdAndTransactionId(
            event.getOrderId(),
            event.getTransactionId()
    )) {
      throw  new ValidationException("There's another transactionId for this validation!");
    }
  }

  private void createPendingPayment(Event event) {
    var totalItems = calculateTotalItems(event);
    var amount = calculateAmount(event);
    var payment = Payment.builder()
            .orderId(event.getPayload().getId())
            .transactionId(event.getTransactionId())
            .totalAmount(amount)
            .totalItems(totalItems)
            .build();

    save(payment);
    setEventAmountItem(event, payment);
  }

  private double calculateAmount(Event event) {
    return event.getPayload()
            .getProducts()
            .stream()
            .map(producer -> producer.getQuantity() * producer.getProduct().getUnitValue())
            .reduce(REDUCE_SUM_VALUE, Double::sum);
  }

  private int calculateTotalItems(Event event) {
    return event.getPayload()
            .getProducts()
            .stream()
            .map(OrderProduct::getQuantity)
            .reduce(REDUCE_SUM_VALUE.intValue(), Integer::sum);

  }

  private void setEventAmountItem(Event event, Payment payment) {
    event.getPayload().setTotalAmount(payment.getTotalAmount());
    event.getPayload().setTotalItems(payment.getTotalItems());
  }

  private void validateAmount(double amount) {
    if (amount < MIN_AMOUNT_VALUE) {
      throw new ValidationException("The minimum amount available is ".concat(MIN_AMOUNT_VALUE.toString()));
    }
  }

  private void changePaymentToSuccess(Payment payment) {
    payment.setStatus(EPaymentStatus.SUCCESS);
    save(payment);
  }

  private void handleSuccess(Event event) {
    event.setStatus(SUCCESS);
    event.setSource(CURRENT_SOURCE);
    addHistory(event,  "Payment realized successfully!");
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
  
  public void realizeRefund(Event event) {
    event.setStatus(FAIL);
    event.setSource(CURRENT_SOURCE);

    try {
      changePaymentStatusToRefund(event);
      addHistory(event,  "Rollback executed for payment!");
    } catch (Exception ex) {
      addHistory(event, "Rollback not executed for payment: ".concat(ex.getMessage()));
    }

    producer.sendEvent(jsonUtil.toJson(event));
  }

  private void changePaymentStatusToRefund(Event event) {
    var payment = findByOrderIdAndTransactionId(event);
    payment.setStatus(REFUND);
    setEventAmountItem(event, payment);
    save(payment);
  }

  private Payment findByOrderIdAndTransactionId(Event event) {
    return repository
            .findByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId())
            .orElseThrow(() -> new ValidationException("Payment not found by OrderId and TransactionId!"));
  }

  private  void save(Payment payment) {
    repository.save(payment);
  }
}
