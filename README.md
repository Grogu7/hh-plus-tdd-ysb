# Point Service - Concurrency Control

## 1. 문제 배경
- 현재 서비스는 **인메모리 저장소**(`UserPointTable`, `PointHistoryTable`)를 사용한다.
- DB 수준의 **락, 트랜잭션 격리, 버전 관리** 기능은 사용할 수 없다.
- 따라서 **애플리케이션 레벨**에서 동시성 제어를 고려해야 한다.

## 2. 동시성 문제가 발생하는 상황
- 하나의 JVM 서버에서 여러 요청이 동시에 들어올 수 있음
    - 요청마다 스레드 풀에서 스레드가 할당되어 병렬 실행됨
    - 모든 스레드는 같은 인메모리 자원(`HashMap`)을 공유
- **충돌 가능성**:
    - 같은 사용자 ID에 대해 `charge` / `use` 요청이 동시에 실행되는 경우
    - 결과적으로 포인트 값이 잘못 누적되거나, 부족한데 사용되는 문제가 발생할 수 있음

➡ 이런 **공유 자원을 동시에 접근하는 코드 블록**을 **임계구역(critical section)** 이라고 한다.

---

## 3. 고려한 동시성 제어 방식

### 3.1 DB 레벨 제어 (불가)
- `SELECT ... FOR UPDATE`, Optimistic Lock(@Version) 등
- 하지만 현재 구조는 DB를 사용하지 않으므로 적용할 수 없음

### 3.2 버전 관리 방식 (불가)
- 엔티티 버전 필드를 두고 갱신 시 충돌 감지
- 마찬가지로 현재 과제 구조에서는 불가

### 3.3 애플리케이션 레벨 제어 (가능)

#### (1) `synchronized` 키워드 사용
- 사용자 ID별로 **락 객체**를 두고, 해당 락을 획득한 스레드만 포인트 변경 로직 실행
- 예시 코드:

```java
private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();

public UserPoint charge(long userId, long amount) {
    Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
    synchronized (lock) {
        UserPoint current = userPointTable.selectById(userId);
        long updated = Math.addExact(current.point(), amount); // overflow-safe
        return userPointTable.insertOrUpdate(userId, updated);
    }
}
```

- 장점
    - 단순하고 직관적이다!
- 단점
    - JVM 인스턴스 단위로만 유효 (서버 여러 대일 경우에는 무의미)
    - 한 서버 내에서 멀티 스레드 문제는 해결되지만...서버가 여러대라면 소용없다.

---

#### (2) 사용자별 큐(Queue) 직렬화
- 각 사용자 ID에 대한 요청을 **큐에 적재**하고, **전용 워커 스레드**가 순차적으로 처리
- 서로 다른 사용자 요청은 병렬 실행 가능
- 예시 구조:
    - `Map<Long, BlockingQueue<Request>> userQueues`
    - 요청 도착 시 해당 사용자 큐에 push
    - 큐에서 순차적으로 요청을 꺼내서 처리한다면 동시성 문제는 없을 것이다.

- 단점
    - 구현이 복잡하다. synchronized 하나로 처리할 수도 있는데...
    - 큐 관리, 스레드 관리 비용 발생

---

## 4. 결론
- 현재 인메모리 기반 과제에서는 **사용자별 synchronized 락**이 가장 단순하고 실용적이다.
- 실무에서는 보통 DB를 사용하므로 **DB 트랜잭션 락**을 통해 동시성 제어를 할 수 있다. 애플리케이션 레벨 제어는 보조 수단으로 활용된다.


