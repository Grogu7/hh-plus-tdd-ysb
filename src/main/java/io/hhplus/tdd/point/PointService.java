package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService implements IPointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    @Override
    public UserPoint point(long id) {
        return this.userPointTable.selectById(id);
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

        long updated = safeAdd(this.userPointTable.selectById(id).point(), amount);
        this.pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());
        return this.userPointTable.insertOrUpdate(id, updated);
    }

    @Override
    public UserPoint use(long id, long amount) {
        if(amount <= 0) throw new IllegalArgumentException("올바르지 않은 포인트를 입력하였습니다.");
        long curr = this.userPointTable.selectById(id).point();
        if(curr < amount) throw new IllegalStateException("포인트가 부족합니다.");

        UserPoint updated = this.userPointTable.insertOrUpdate(id, curr - amount);
        this.pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

        return updated;
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
}
