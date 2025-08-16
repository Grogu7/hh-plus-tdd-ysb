package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PointService implements IPointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    /*
    * user id별 락을 위한 ConcurrentHashMap
    * HashMap : thread 안전하지 못함. 버킷 리사이징 시, 원자성이 보장되지 않는 문제 있음. 멀티스레드 환경에서는 ConcurrentHashMap 사용.
    * */
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }
    /*
    * 조회에도 락이 필요한 지?
    * 성능과 일관성의 trade-off
    * 포인트, 금액는 일관성이 중요한 도메인이라고 생각됨
    * */
    @Override
    public UserPoint point(long id) {
        Object lock = lockOf(id);
        synchronized (lock) {
            return this.userPointTable.selectById(id);
        }
    }

    @Override
    public List<PointHistory> history(long id) {
        return this.pointHistoryTable.selectAllByUserId(id);
    }

    @Override
    public UserPoint insertOrUpdate(long id, long amount) {
        return this.userPointTable.insertOrUpdate(id, amount);
    }

    @Override
    public UserPoint charge(long id, long amount) {
        if(amount <= 0) throw new IllegalArgumentException("올바르지 않은 포인트를 입력하였습니다.");
        Object lock = lockOf(id);
        synchronized (lock) {
            long updated = safeAdd(this.userPointTable.selectById(id).point(), amount);
            this.pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());
            return this.userPointTable.insertOrUpdate(id, updated);
        }
    }

    @Override
    public UserPoint use(long id, long amount) {
        if(amount <= 0) throw new IllegalArgumentException("올바르지 않은 포인트를 입력하였습니다.");

        Object lock = lockOf(id);
        synchronized (lock) {
            long curr = this.userPointTable.selectById(id).point();
            if(curr < amount) throw new IllegalStateException("포인트가 부족합니다.");

            UserPoint updated = this.userPointTable.insertOrUpdate(id, curr - amount);
            this.pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

            return updated;
        }
    }

    private long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) {
            throw new ArithmeticException("Overflow");
        }
        if (b < 0 && a < Long.MIN_VALUE - b) {
            throw new ArithmeticException("Underflow");
        }
        return a + b;
    }

    private Object lockOf(long userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }
}
