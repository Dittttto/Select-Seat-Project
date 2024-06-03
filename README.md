# 이선좌: 이미 선택된 좌석입니다

## Table of Contents

- [이선좌: 이미 선택된 좌석입니다](#이선좌-이미-선택된-좌석입니다)
  - [Table of Contents](#table-of-contents)
  - [Introduction](#introduction)
  - [Demonstraion](#demonstraion)
  - [Development](#development)
    - [Architecture](#architecture)
    - [Prerequisites](#prerequisites)
      - [Backend](#backend)
      - [Database](#database)
      - [Infrastructure](#infrastructure)
      - [Monitoring](#monitoring)
      - [Frontend](#frontend)
      - [Load Test](#load-test)
  - [Concern and solution process](#concern-and-solution-process)
  - [About](#about)

## Introduction

**이선좌** 프로젝트의 자바 기반 스프링 부트를 활용한 백엔드 어플리케이션입니다. 자세한 내용은 WIKI를 참조해주세요. (WIKI는 추후에 추가 예정입니다.)

## Demonstraion

http://selected-seat.shop

## Development

### Architecture

![architecture](https://github.com/Selected-Seat/Select-Seat/assets/65538799/52c0e5cc-7116-4b75-b796-93984950db4e)

### Prerequisites

#### Backend

- JDK 17
- SpringBoot 3.2.x
- SpringBatch 5.1.x
- SpringSecurity 3.2.x
- SpringWebFlux 3.2.x
- ElasticSearch 8.11.x

#### Database

- MongoDB 4.2.x
- MySQL 8.1.x
- Redis 7.0.x

#### Infrastructure

- aws ECS
- aws ECR
- aws Route53
- aws EC2
- aws S3
- aws VPC
- aws SES
- aws RDS
- aws ElastiCache
- aws IAM
- aws ALB
- aws NLB
- aws AutoScailing
- docker 23.0.x
- docker-compose 2.17.x
- jenkins 2.426.x

#### Monitoring

- prometheus 2.51.x
- grafana 10.4.x
- Kibana 8.11.x
- aws cloudwatch

#### Frontend

- Vue 3.4.x
- Vue Router 4.3.0
- Node 21.7.x
- Pinia 2.1.x
- Bootstrap 5.3.x
- axios 1.6.x
- thymeleaf 3.1.x

#### Load Test

- Vegeta
- Jmeter

## Concern and solution process

<details>
<summary><h3 style="display: inline-block;"> [Batch] @EnableBatchProcessing 적용시 배치 잡이 동작하지 않는 문제</h3></summary>

| 원인

SpringBoot 3.0 부터 `DefaultBatchConfiguration `클래스나 `EnableBatchProcessing` 어노테이션을 선언할 경우 배치 잡의 자동 실행하는 것을 제한하는 로직이 추가되었다.

```java
@AutoConfiguration(after = { HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class })
@ConditionalOnClass({ JobLauncher.class, DataSource.class, DatabasePopulator.class })
@ConditionalOnBean({ DataSource.class, PlatformTransactionManager.class })
@ConditionalOnMissingBean(value = DefaultBatchConfiguration.class, annotation = EnableBatchProcessing.class)
@EnableConfigurationProperties(BatchProperties.class)
@Import(DatabaseInitializationDependencyConfigurer.class)
public class BatchAutoConfiguration {
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "spring.batch.job", name = "enabled", havingValue = "true", matchIfMissing = true)
	public JobLauncherApplicationRunner jobLauncherApplicationRunner(JobLauncher jobLauncher, JobExplorer jobExplorer,
			JobRepository jobRepository, BatchProperties properties) {
		JobLauncherApplicationRunner runner = new JobLauncherApplicationRunner(jobLauncher, jobExplorer, jobRepository);
		String jobNames = properties.getJob().getName();
		if (StringUtils.hasText(jobNames)) {
			runner.setJobName(jobNames);
		}
		return runner;
	}
    ...
}
```

위의 코드에서 `@ConditionalOnMissingBean(value = DefaultBatchConfiguration.class, annotation = EnableBatchProcessing.class)` 부분을 통해서 `DefaultBatchConfiguration` 혹은 `EnableBatchProcessing이` 정의된 빈이 있을 경우 스프링에 등록된 기본 빈을 사용하지 않고, 등록된 빈을 사용하게 된다. 현재의 코드에서는 `@EnableBatchProcessing` 어노테이션이 적용되어 있고, 내부에는 자동 실행 로직이 없기 때문에, 등록된 배치 잡이 실행되지 않았던 것이다.

| 문제해결

배치 서버가 실행됨과 동시에 실행되어야 하는 배치 잡은 없기 때문에, `jobLauncher`를 통해서 배치 잡을 `api call` 혹은 `스케줄러`를 통해 실행할 수 있도록 변경했다.

```java
public void createTickets(
        final JobParameters jobParameters
    ) {
        try {
            jobLauncher.run(ticketCreateJob, jobParameters);
        } catch (
            JobExecutionAlreadyRunningException |
            JobInstanceAlreadyCompleteException |
            JobParametersInvalidException |
            JobRestartException e
        ) {
            throw new RuntimeException(e);
        }
    }
```

</details>

<details>
<summary><h3 style="display: inline-block;"> [Batch] 동일한 스텝과 잡이 실행되지 않는 문제</h3></summary>

| 원인

스프링 배치는 기본적으로 잡과 스텝의 상태를 저장하고 이에 기반하여 잡과 스텝의 실행을 1번만 수행할 수 있도록 보장한다. 배치 잡이 실행되면 `JobInstance`가 생성된다. 이때 `JobInstance`는 잡의 논리적 실행을 나타내며 두 가지 항목으로 식별되는데, 하나는 잡의 이름이고 하나는 잡이 실행될때 전달된 파라미터다. 그리고 이때 저장된 이름과 파라미터를 이용해서 실행되었던 잡인지 식별하게되고, 실행되었던 잡은 실행되지 않도록 하는 것이다. 이렇게 함으로써 다중으로 같은 잡이 실행되는 문제를 해결할 수 있다. 하지만 같은 잡 또는 스텝을 실행하야 하는 경우가 발생할 수 있다. 이선좌 프로젝트에서도 동일한 스텝을 반복적으로 실행하는 경우와 테스트를 위해서 잡을 반복 실행하는 경우가 발생하였다.

| 문제해결

스프링 배치에서는 잡과 스텝을 재시작 할 수 있는 다양한 방법을 제공한다. 먼저 잡을 재실행하는 방법으로는 파라미터에 실행 날짜를 전달하여 동작시키는 방법이 있지만, 여기에서는 `RunIdIncrement` 객체를 사용했다. `RunIdIncrement`를 적용하면 배치 잡 실행시 `run.id` 파라미터를 생성하고, 잡을 반복 호출시 `run.id`의 값을 증가시킨다. 이렇게 되면 지속적으로 파라미터가 변경되기 때문에 잡에 대한 반복호출이 가능해진다. `BATCH_JOB_EXECUTION_PARAMS`에서 `run.id` 필드가 추가되고 등가되는 것을 확인할 수 있다.

```java
return new JobBuilder("job", jobRepository)
    .start(step)
    .incrementer(new RunIdIncrementer())
    .build();
```

스텝의 경우 `allowStartIfComplete` 의 값을 `true` 로 설정하면 동일한 파라미터로 스텝을 실행해도 반복 실행이 가능하다.

```java
return new StepBuilder("step", jobRepository)
    .reader(reader)
    .processor(processor)
    .writer(writer)
    .allowStartIfComplete(true)
    .build();
```

| 참고

- https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing/restart.html
- https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/launch/support/RunIdIncrementer.html

</details>


<details>
<summary><h3 style="display: inline-block;"> [Batch] 청크 사이즈 기준</h3></summary>

| 고민

배치의 각 스텝에서는 `JpaPagingItemReader`를 통해서 정해진 `Chunk Size`만큼 데이터를 처리할 수 있다. 이때 `chink size` 를 매우 크게 잡아서 큰 단위로 데이터를 처리하면 속도가 빠르다고 생각할 수 있지만 그렇지 않다. `Chunk` 단위로 데이터를 처리한 다는 것은 `chunk` 가 트랜잭션의 단위가 된다는 것과 같다. 이는 트랜잭션이 실패하여 롤백이 되는 순간 지정한 `chunk` 만큼의 데이터가 롤백된다는 것을 의미하고, 너무 큰 `chunk` 사이즈는 I/O 비용을 절약할 수 있지만 트랜잭션 비용에 대한 오버헤드가 발생할 수 있다. 그렇다면 적절한 `chunk` 사이즈의 기준은 무엇일까? 아쉽게도 찾지 못했다. 배치를 수행하는 서버의 스펙에 맞게 조절하는 수 밖에 없는 것이다.

| 고민해결

적절한 `chunk` 사이즈를 도출하기 위해서 실제 배포된 배치 서버에서 부하 테스트를 수행하였다. 테스트는 약 6만개의 좌석을 생성하고 등록하는 `api call`을 기준으로 배치 잡의 실행시간을 측정하였다.

결과는 다음과 같다.

| chunk size | execution time |
| ---------- | -------------- |
| 1          | 15m 44s 566ms  |
| 50         | 2m 18s 539ms   |
| 100        | 2m2s489ms      |
| 500        | 1m44s630ms     |
| 1000       | 1m43s952ms     |
| 1500       | 1m29s433ms     |
| 2000       | 1m52s78ms      |

측정된 결과를 기반으로 `chunk` 사이즈가 500개인 부분부터 임계점에 도달했다고 판단했고, 500개와 1000개 사이인 **750개**의 `chunk` 사이즈로 최종 결정하였다. 하지만 현재의 750개가 언제나 정답일 수는 없다. 변화하는 서버의 스팩과 지속적인 모니터링으로 튜닝을 수행해야한다.
</details>

<details>
<summary><h3 style="display: inline-block;">[Batch] SimpleAsyncTaskExecutor 적용으로 인한 동시성 문제</h3></summary>

| 원인

`Spring Batch`는 `SimpleAsyncTaskExecutor`를 이용하여 스텝의 동작을 멀티 스레드로 동작시킬 수 있다. 하지만 이때 주의해야 하는 부분은 스텝의 요소가 스레드 세이프해야한 다는 것이다. 관련 내용은 아래의 공식 문서 발췌본에서 확인할 수 있다.

> Spring Batch provides some implementations of `ItemWriter` and `ItemReader`. Usually, they say in the Javadoc if they are thread safe or not or what you have to do to avoid problems in a concurrent environment. If there is no information in the Javadoc, you can check the implementation to see if there is any state. If a reader is not thread safe, you can decorate it with the provided `SynchronizedItemStreamReader` or use it in your own synchronizing delegator. You can synchronize the call to `read()`, and, as long as the processing and writing is the most expensive part of the chunk, your step may still complete much more quickly than it would in a single-threaded configuration.

| 문제해결

문서를 확인하면 `SynchronizedItemStreamReader` 를 사용하여 스레드 세이프한 IremReader를 이용할 수 있는 것을 알 수 있다. 그렇다면 내부적으로 어떻게 구현되어 있기에 스레드 세이프 한 것인지 내부 코드를 살펴보면 다음과 같다.

```java
public class SynchronizedItemStreamReader<T> implements ItemStreamReader<T>, InitializingBean {
    private ItemStreamReader<T> delegate;
    private final Lock lock = new ReentrantLock();

    @Nullable
    public T read() throws Exception {
        this.lock.lock();

        Object var1;
        try {
            var1 = this.delegate.read();
        } finally {
            this.lock.unlock();
        }

        return var1;
    }
}
```

`read()` 메서드를 확인하면 `java.util.concurrent` 패키지의 `Lock` 을 적용하여, 스레드 세이프함 보장하고 있다. `ReentrantLock` 은 재진입이 가능한 `Lock`으로, 가장 일반적인 배타 `Lock`이다. 이를 참고하여 `Custom item reader`에 동일한 `ReentrantLock` 을 적용하여 스레드 세이프하게 구현하였다. 결과는 성공적으로 멀티 스레드로 스텝이 동작하는 것을 확인할 수 있었고, 싱글 스레드 대비 **75% 실행 시간을 단축할 수 있었다.**

```java
@Override
public TicketBatchEntity read() throws Exception {
    this.lock.lock();
    TicketBatchEntity next = null;

    try {
        if (iterator.hasNext()) {
            next = iterator.next();
        }
    } finally {
        this.lock.unlock();
    }

    return next;
}
```

</details>

<details>
<summary><h3 style="display: inline-block;">[Batch] 독립적인 Step 의 결과를 공유해야 하는 문제</h3></summary>

| 원인

스텝은 잡을 구성하는 독립적인 작업의 단위이다. 여기서 독립적이라는 말은 각 스텝은 의존적일 수 없다는 것이다. 하지만 티켓을 만료하는 잡에서는 만료된 콘서트를 조회하고(조회 스텝), 티켓을 만료하는 스텝에서 조회 스텝의 결과를 참조해야 하는 문제가 발생하였다.

| 문제해결

스프링 배치가 제공하는 대부분의 `ItemReader`는 상태를 유지하므로 스테이트풀하다. 이는 `StepExecutionContext`에 상태를 저장하고 관리하기 때문이다. 스프링 배치는 `ExecutionContext`를 잡과 스텝을 구분해서 관리하는데, 이때 스텝의 ExectionContext 내용을 잡으로 승격시키면 각 스텝에서 동일한 상태를 공유할 수 있게 된다. 이를 위해서 스프링 배치는 `ExecutionContextPromotionListener` 을 제공한다. `ExecutionContextPromotionListener` 은 스텝이 종료되면 `StepExectuion`에 저장된 상태를 `JobExecution` 참조할 수 있도록 자동으로 승격해준다. 내부의 코드를 살펴보면 다음과 같이 네모 박스 부분에서 해당 동작에 대한 구현 부분을 확인할 수 있다.

그리고 ExecutionContextPromotionListener 는 StepExecutionListener 의 구현체이기 때문에 간단하게 listener 로 등록해서 사용이 가능하다.

```java
@Bean
public ExecutionContextPromotionListener concertDatePromotionListener() {
    final ExecutionContextPromotionListener executionContextPromotionListener
        = new ExecutionContextPromotionListener();

    executionContextPromotionListener
        .setKeys(new String[]{"concertExpiredMap"});

    return executionContextPromotionListener;
}
```

```java
    @Bean
    public Step concertDateReadJob(
        final ItemReader<ConcertDateEntity> concertDateItemReader,
        final ItemWriter<ConcertDateEntity> concertDateItemWriter,
        final ExecutionContextPromotionListener concertDatePromotionListener
    ) {
        return new StepBuilder("concertDateReadJob", jobRepository)
            .<ConcertDateEntity, ConcertDateEntity>chunk(CHUNK_SIZE,
                platformTransactionManager)
            .reader(concertDateItemReader)
            .writer(concertDateItemWriter)
            .listener(concertDatePromotionListener) <- listener 적용
            .allowStartIfComplete(true)
            .build();
    }
```
</details>

<details>
<summary><h3 style="display: inline-block;">[Batch] 복수의 테이블 조회를 통한 조회 성능 문제</h3></summary>

| 고민

공연에 대한 사전 알림을 발송하기 위해서는 4개 이상의 테이블을 조회해야 하는 경우가 발생했다. 하나의 `Join` 쿼리를 적용할 수 있지만, 적은 양의 데이터라면 성능에 문제가 없지만 대용량의 데이터가 적재된 4개 이상의 테이블에 적용하는 것은 성능 저하의 원인이 된다.

| 고민해결

서전 알림에 사용되는 데이터는 90% 이상이 조회성 데이터이다. 또한, 기획에 따라서 필요한 데이터의 형태가 지속적으로 변경될 수 있다고 판단했다. 이를 위해서 대용량의 데이터를 빠르게 조회할 수 있고, 정해진 스키마가 없이 데이터를 적재할 수 있는 `NoSQL` 을 도입하기로 결정하였고, 빠른 조회를 바탕으로 공연 사전 알림 서비스를 구현할 수 있었다.
</details>


<details>
<summary><h3 style="display: inline-block;">[Queuing system] 대용량 트래픽을 처리하기 위한 Webflux</h3></summary>

| 고민

티켓팅은 순간 접속자가 많고 대용량이 트래픽이 몰리게 된다.실제 티켓팅 서비스의 경우 최근 가수 아이유의 콘서트에 85만명 동접속자수가 발생하였고, 인기있는 일반 콘서트도 좌석의 배수 인원 만큼 접속할 것으로 예상할 수 있다. 이때 Blocking 방식의 톰캣을 기반으로 하는 Spring MVC는 요청에 대해 하나의 스레드를 할당 하는 방식으로 동작하게 된다. 이는 대량의 트래픽을 빠르게 처리하는 것에는 적합하지 않다고 판단했다.

| 고민해결

대량의 트래픽을 보다 빠르고 안정적으로 처리할 수 있는 서버가 필요하였고, Non-Blocking Netty 기반의 Webflux를 선택하게 되었다. 다음은 Netty 서버와 Webflux가 요청을 받았을때 수행되는 흐름을 도식화 한 것이다.

요청을 받으면 이벤트 루프에서 요청을 Channel pipleline의 Channel handler에서 위임하고, 이때 요청에 대한 콜백을 등록한다. handler에서 요청을 처리하고 응답에 대한 이벤트를 발행하면 이벤트 루프에서 이전에 등록된 콜백을 실행하여 응답하게 된다. 이러한 방식은 요청당 스레드를 할당하는 것이 아니라 하나의 스레드가 쉬지않고 더 많은 요청을 처리할 수 있다는 점이 webflux 빠른이유의 기반이된다.

이러한 이유를 바탕으로 Webflux를 대기열 시스템의 메인 서버로 선택하였고, 로컬에서 부하테스트를 진행하였다.

요청: 2백만 건에 대한 조회 요청

| Target | Samples | Error % | Throughput (/sec) |
| --- | --- | --- | --- |
| Spring MVC | 2000000 | 0.0 | 16177/sec |
| Spring Webflux | 2000000 | 0.0 | 42787/sec |

결과를 통해 Spring Webflux가 Spring Web 대비 `약 3배이상 높은 처리율`을 가진 다는 것을 확인할 수 있었다.
</details>


<details>
<summary><h3 style="display: inline-block;">Gradle Multi-module</h3></summary>

| 고민

기존 모놀리식으로 프로젝트를 구현하면서 다음과 같은 불편함이 있었다.

1. 단일 모듈에서 패키지만으로 레이어를 분리했다.
2. 모든 의존성이 하나의 모듈에 집약되어 있다.
3. 외존성이 거미줄 처럼 엮여있다.

| 고민해결

이러한 불편함을 개선하고자 처음 고려한 것은 도메인을 기준으로 모듈을 분리하는 것이었다. 하지만 도메인을 기준으로 모듈을 분리시 모듈내부의 복잡도가 증가하게 되는 문제가 있었고 MSA 도입이 더 적절한 선택이라고 판단하였다. 하지만 마감기한과 러닝 커브를 고려하여 레이어를 기준으로 모듈을 분리하고, 필요한 의존성간의 협력관계를 구성하기 위해서 노력했다.


</details>

## About

Credit to @Selected-Seat/selected-seat : @Dittttto, @gunnu3226, @RamuneOrch and @sonjh919
