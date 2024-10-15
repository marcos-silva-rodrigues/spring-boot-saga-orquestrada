# Sobre

Este projeto é um estudo do curso da udemy sobre Padrão Saga Orquestrado ministrado pelo [Victor Hugo](https://www.udemy.com/user/victor-hugo-negrisoli/),
disponivel [neste link][https://www.udemy.com/course/arquitetura-de-microsservicos-padrao-saga-orquestrado/?couponCode=ST8MT101424](https://www.udemy.com/course/arquitetura-de-microsservicos-padrao-saga-orquestrado)

Aonde foi discutido assuntos sobre:
+ Arquitetura assíncrona
+ Transações distribuídas
+ Two Phase Commit
+ Outbox Pattern
+ Sagas Orquestradas e Coreografadas

O Projeto se basea em uma arquitetura baseada em eventos, aonde as transações de uma simulação de compra, passaria por etapas como:
- Validação da ordem de compra
- Uma simulação de um gateway de pagamento
- Gestão de Inventario

As integrações são feitas por meio de eventos utilizando Kafka, e são gerenciadas por um serviços orquestrado, responsável por
conhecer o fluxo de negocio e lidar com cada etapa de forma correta, assim também como lidar com falhas em qualquer etapa do fluxo
