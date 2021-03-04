package pers.binaryhunter.db.mybatis.datasource.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.db.mybatis.datasource.MyDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于权重轮询算法
 * Algorithm is as follows: on each peer selection we increase current_weight of each eligible peer by its weight,
 * select peer with greatest current_weight and reduce its current_weight by total number of weight points distributed among peers.
 */
public class MyRandomRWDataSourceRouter extends AbstractRWDataSourceRouter {
    private static final Logger log = LoggerFactory.getLogger(MyRandomRWDataSourceRouter.class);

    private boolean first = true;
    private int totalWeight = 0;
    private AtomicInteger count = new AtomicInteger(0);

	@Override
	protected DataSource loadBalance() {
	    List<DataSource> list = getResolvedReadDataSources();
	    if(first) {
	        first = false;
            synchronized (log) {
                if (0 >= totalWeight) {
                    for (DataSource ds : list) {
                        MyDataSource mds = (MyDataSource) ds;
                        if (0 >= mds.getWeight()) {
                            mds.setWeight(1);
                        }
                        totalWeight += mds.getWeight();
                    }
                }
            }
        }

	    MyDataSource maxMds = null;
        for(DataSource ds : list) {
            MyDataSource mds = (MyDataSource) ds;
            // 每个节点，用它们的当前值加上它们自己的权重。
            mds.setCurrent(mds.getCurrent() + mds.getWeight());

            if(null == maxMds || maxMds.getCurrent() < mds.getCurrent()) { //发现最大值
                maxMds = mds;
            }
        }
        //选择当前值最大的节点为选中节点，并把它的当前值减去所有节点的权重总和。
        if(null != maxMds) {
            if (log.isDebugEnabled()) log.debug("Max current {}/{}", maxMds.getCurrent(),  maxMds.getWeight());
            maxMds.setCurrent(maxMds.getCurrent() - totalWeight);
            return maxMds;
        }

        int index = Math.abs(count.incrementAndGet()) % getReadDsSize();
		return getResolvedReadDataSources().get(index);
	}

}
