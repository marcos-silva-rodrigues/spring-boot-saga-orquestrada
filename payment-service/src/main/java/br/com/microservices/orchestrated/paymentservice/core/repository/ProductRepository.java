package br.com.microservices.orchestrated.paymentservice.core.repository;

import br.com.microservices.orchestrated.paymentservice.core.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {

  Boolean existsByCode(String code);
}
