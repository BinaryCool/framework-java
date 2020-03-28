package pers.binaryhunter.db.mybatis.datasource.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.db.mybatis.datasource.MyDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单实现读数据源负载均衡
 */
public class MyRandomRWDataSourceRouter extends AbstractRWDataSourceRouter {
    private static final Logger log = LoggerFactory.getLogger(MyRandomRWDataSourceRouter.class);

    private boolean first = true;
    private AtomicInteger totalWeight = new AtomicInteger(0);
	private Random random = new Random();
    private AtomicInteger count = new AtomicInteger(0);

	@Override
	protected DataSource loadBalance() {
	    List<DataSource> list = getResolvedReadDataSources();
	    if(first) {
	        first = false;
            synchronized (log) {
                if (0 >= totalWeight.get()) {
                    for (DataSource ds : list) {
                        MyDataSource mds = (MyDataSource) ds;
                        if (0 >= mds.getWeight()) {
                            mds.setWeight(1);
                        }

                        mds.setMin(totalWeight.incrementAndGet());
                        mds.setMax(totalWeight.addAndGet(mds.getWeight()));
                    }
                }
            }
        }

	    int rmIndex = random.nextInt(totalWeight.get()) + 1;
        for(DataSource ds : list) {
            MyDataSource mds = (MyDataSource) ds;
            if(0 >= mds.getMin() || 0 >= mds.getMax()) {
                continue;
            }
            if(mds.getMin() <= rmIndex && mds.getMax() >= rmIndex) {
                log.debug("Data source route by Weight: rmIndex {}, min {}, max {}, totalWeight {}", rmIndex, mds.getMin(), mds.getMax(), totalWeight.get());
                return ds;
            }
        }

        int index = Math.abs(count.incrementAndGet()) % getReadDsSize();
		return getResolvedReadDataSources().get(index);
	}

}
