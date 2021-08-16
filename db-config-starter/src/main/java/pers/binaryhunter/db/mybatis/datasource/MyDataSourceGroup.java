package pers.binaryhunter.db.mybatis.datasource;

import com.alibaba.druid.pool.DruidDataSource;

import java.util.ArrayList;
import java.util.List;

public class MyDataSourceGroup {
    private DruidDataSource writeDataSource;
    private List<MyDataSource> readDataSources = new ArrayList<>();

    public DruidDataSource getWriteDataSource() {
        return writeDataSource;
    }

    public void setWriteDataSource(DruidDataSource writeDataSource) {
        this.writeDataSource = writeDataSource;
    }

    public List<MyDataSource> getReadDataSources() {
        return readDataSources;
    }

    public void setReadDataSources(List<MyDataSource> readDataSources) {
        this.readDataSources = readDataSources;
    }
}
