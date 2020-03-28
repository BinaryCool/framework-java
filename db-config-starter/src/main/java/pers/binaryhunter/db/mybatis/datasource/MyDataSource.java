package pers.binaryhunter.db.mybatis.datasource;

import com.alibaba.druid.pool.DruidDataSource;
public class MyDataSource extends DruidDataSource {
    private int weight = 1;
    private int min;
    private int max;

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }
}
