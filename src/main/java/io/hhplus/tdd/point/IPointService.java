package io.hhplus.tdd.point;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface IPointService {

    public UserPoint point(long id);
    public List<PointHistory> history (long id);
    public UserPoint insertOrUpdate(long id, long amount);

    public UserPoint charge(long id, long amount);
    public UserPoint use(long id, long amount);
}
