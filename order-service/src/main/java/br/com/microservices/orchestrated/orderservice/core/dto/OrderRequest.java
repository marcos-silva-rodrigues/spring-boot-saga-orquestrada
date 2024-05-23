package br.com.microservices.orchestrated.orderservice.core.dto;

import br.com.microservices.orchestrated.orderservice.core.document.OrderProduct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

  private List<OrderProduct> products;

}
