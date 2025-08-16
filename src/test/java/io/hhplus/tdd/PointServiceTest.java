package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*; // assertThat, assertThatThrownBy

class PointServiceTest {

    @Test
    @DisplayName("신규 유저 조회시 0 포인트")
    void point_returnsZeroForNewUser() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());

        UserPoint up = svc.point(1L);

        assertThat(up.point()).isZero();
    }

    // --- charge ---

    @Test
    @DisplayName("단건 충전: 금액만큼 잔액 증가")
    void charge_single_success() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());

        UserPoint after = svc.charge(10L, 1000L);

        assertThat(after.point()).isEqualTo(1000L);
        List<PointHistory> h = svc.history(10L);
        assertThat(h).hasSize(1);
        assertThat(h.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(h.get(0).amount()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("다건 충전: 누적")
    void charge_multiple_success() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());
        long userId = 11L;

        svc.charge(userId, 300L);
        UserPoint after = svc.charge(userId, 700L);

        assertThat(after.point()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("0 또는 음수 충전: IllegalArgumentException")
    void charge_nonPositive_fails() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());

        assertThatThrownBy(() -> svc.charge(12L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.charge(12L, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("오버플로우 충전: ArithmeticException")
    void charge_overflow_fails() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());
        long userId = 13L;
        //safeAdd 검증.

        svc.charge(userId, Long.MAX_VALUE - 5);
        assertThatThrownBy(() -> svc.charge(userId, 10))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("사용: 잔액 충분하면 차감되고 히스토리 기록")
    void use_decreases_whenSufficient() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());
        long userId = 20L;

        svc.charge(userId, 2000L);
        UserPoint after = svc.use(userId, 500L);

        assertThat(after.point()).isEqualTo(1500L);
        List<PointHistory> h = svc.history(userId);
        assertThat(h).hasSize(2);
        assertThat(h.get(1).type()).isEqualTo(TransactionType.USE);
        assertThat(h.get(1).amount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("사용: 잔액 부족이면 IllegalStateException")
    void use_fails_whenInsufficient() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());

        assertThatThrownBy(() -> svc.use(21L, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("사용: 0 또는 음수면 IllegalArgumentException")
    void use_nonPositive_fails() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());

        assertThatThrownBy(() -> svc.use(22L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.use(22L, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("충전-사용-사용: 최종 잔액, 이력 확인")
    void scenario_charge_then_use_then_use() {
        PointService svc = new PointService(new UserPointTable(), new PointHistoryTable());
        long userId = 30L;

        svc.charge(userId, 1000L);   // 1000
        svc.use(userId, 400L);       // 600
        UserPoint after = svc.use(userId, 100L); // 500

        assertThat(after.point()).isEqualTo(500L);
        List<PointHistory> h = svc.history(userId);
        assertThat(h).hasSize(3);
        assertThat(h).extracting(PointHistory::type)
                .containsExactly(TransactionType.CHARGE, TransactionType.USE, TransactionType.USE);
        assertThat(h).extracting(PointHistory::amount)
                .containsExactly(1000L, 400L, 100L);
    }

    @Test
    @DisplayName("락 O: 같은 유저 두 번 동시 사용 → 한쪽은 예외, 최종 잔액 0")
    void withLock_doubleSpend_prevented_invokeAll() throws Exception {
        var upt = new UserPointTable();
        var pht = new PointHistoryTable();
        var svc = new PointService(upt, pht); // userId별 synchronized 적용됨

        long userId = 300L;
        svc.charge(userId, 1000L);

        List<Throwable> errors = new CopyOnWriteArrayList<>();

        ExecutorService es = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> jobs = List.of(
                    () -> { try { svc.use(userId, 1000L); } catch (Throwable t) { errors.add(t); } return null; },
                    () -> { try { svc.use(userId, 1000L); } catch (Throwable t) { errors.add(t); } return null; }
            );
            es.invokeAll(jobs);
        } finally {
            es.shutdown();
        }

        // 두 요청 중 한 건은 반드시 실패(잔액 부족)
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(IllegalStateException.class);

        // 최종 잔액은 정확히 0
        assertThat(svc.point(userId).point()).isEqualTo(0L);
    }


}
