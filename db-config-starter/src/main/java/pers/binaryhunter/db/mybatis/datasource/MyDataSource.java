package pers.binaryhunter.db.mybatis.datasource;

import com.alibaba.druid.pool.DruidDataSource;
public class MyDataSource extends DruidDataSource {
    private int weight = 1;
    private int current = 0;

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }
}
